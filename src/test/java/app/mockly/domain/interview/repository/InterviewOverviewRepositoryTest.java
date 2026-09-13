package app.mockly.domain.interview.repository;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewFeedback;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewSessionStatus;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.product.entity.PlanTier;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static app.mockly.domain.interview.FeedbackTestFixtures.feedbackResult;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class InterviewOverviewRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired InterviewSessionRepository interviewSessionRepository;
    @Autowired InterviewFeedbackRepository interviewFeedbackRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

    @Test
    void weeklySummaryIncludesOnlyLastAnswerSubmittedSessionsInsideTheHalfOpenPeriod() {
        User user = saveUser("weekly");
        Instant periodStart = Instant.parse("2026-08-23T15:00:00Z");
        Instant periodEnd = Instant.parse("2026-08-30T15:00:00Z");
        InterviewSession atStart = saveSession(user, InterviewSessionStatus.FEEDBACK_PENDING,
                FeedbackStatus.PENDING, periodStart);
        InterviewSession feedbackCompleted = saveSession(user, InterviewSessionStatus.COMPLETED,
                FeedbackStatus.COMPLETED, Instant.parse("2026-08-25T00:00:00Z"));
        saveSession(user, InterviewSessionStatus.ABANDONED, null, Instant.parse("2026-08-26T00:00:00Z"));
        saveSession(user, InterviewSessionStatus.IN_PROGRESS, null, Instant.parse("2026-08-27T00:00:00Z"));
        saveSession(user, InterviewSessionStatus.COMPLETED, FeedbackStatus.COMPLETED, periodEnd);

        List<InterviewSession> result = interviewSessionRepository.findLastAnswerSubmittedSessionsInPeriod(
                user.getId(), periodStart, periodEnd);

        assertThat(result).extracting(InterviewSession::getId)
                .containsExactlyInAnyOrder(atStart.getId(), feedbackCompleted.getId());
    }

    @Test
    void recentInterviewsIncludesAbandonedAndReturnsOnlyTheLatestThree() {
        User user = saveUser("recent");
        InterviewSession first = saveSession(user, InterviewSessionStatus.COMPLETED,
                FeedbackStatus.COMPLETED, Instant.parse("2026-08-24T00:00:00Z"));
        InterviewSession second = saveSession(user, InterviewSessionStatus.ABANDONED,
                null, Instant.parse("2026-08-25T00:00:00Z"));
        InterviewSession third = saveSession(user, InterviewSessionStatus.FEEDBACK_PENDING,
                FeedbackStatus.GENERATING, Instant.parse("2026-08-26T00:00:00Z"));
        InterviewSession fourth = saveSession(user, InterviewSessionStatus.COMPLETED,
                FeedbackStatus.COMPLETED, Instant.parse("2026-08-27T00:00:00Z"));

        List<InterviewSession> result = interviewSessionRepository
                .findTop3ByUserIdAndEndedAtIsNotNullOrderByEndedAtDesc(user.getId());

        assertThat(result).extracting(InterviewSession::getId)
                .containsExactly(fourth.getId(), third.getId(), second.getId())
                .doesNotContain(first.getId());
    }

    @Test
    void latestScoresFollowInterviewEndOrderEvenWhenFeedbackWasSavedLater() {
        User user = saveUser("score-order");
        InterviewSession olderSession = saveSession(user, InterviewSessionStatus.COMPLETED,
                FeedbackStatus.COMPLETED, Instant.parse("2026-08-25T00:00:00Z"));
        InterviewSession newerSession = saveSession(user, InterviewSessionStatus.COMPLETED,
                FeedbackStatus.COMPLETED, Instant.parse("2026-08-26T00:00:00Z"));
        InterviewFeedback newerFeedback = saveFeedback(newerSession, 90, "최신 면접 연습 포인트");
        InterviewFeedback olderFeedback = saveFeedback(olderSession, 70, "이전 면접 연습 포인트");
        changeFeedbackCreatedAt(newerFeedback, Instant.parse("2026-08-26T01:00:00Z"));
        changeFeedbackCreatedAt(olderFeedback, Instant.parse("2026-08-27T01:00:00Z"));

        List<Integer> result = interviewFeedbackRepository.findRecentOverallScores(
                user.getId(), PageRequest.of(0, 2));

        assertThat(result).containsExactly(90, 70);
    }

    @Test
    void latestPracticePointFollowsInterviewEndOrderEvenWhenFeedbackWasSavedLater() {
        User user = saveUser("practice-point-order");
        InterviewSession olderSession = saveSession(user, InterviewSessionStatus.COMPLETED,
                FeedbackStatus.COMPLETED, Instant.parse("2026-08-25T00:00:00Z"));
        InterviewSession newerSession = saveSession(user, InterviewSessionStatus.COMPLETED,
                FeedbackStatus.COMPLETED, Instant.parse("2026-08-26T00:00:00Z"));
        InterviewFeedback newerFeedback = saveFeedback(newerSession, 90, "최신 면접 연습 포인트");
        InterviewFeedback olderFeedback = saveFeedback(olderSession, 70, "이전 면접 연습 포인트");
        changeFeedbackCreatedAt(newerFeedback, Instant.parse("2026-08-26T01:00:00Z"));
        changeFeedbackCreatedAt(olderFeedback, Instant.parse("2026-08-27T01:00:00Z"));

        List<String> result = interviewFeedbackRepository.findRecentPracticePoints(
                user.getId(), PageRequest.of(0, 1));

        assertThat(result).containsExactly("최신 면접 연습 포인트");
    }

    private User saveUser(String suffix) {
        return userRepository.save(User.builder()
                .email(suffix + "@example.com")
                .name("테스트 사용자")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("overview-" + suffix)
                .build());
    }

    private InterviewSession saveSession(
            User user,
            InterviewSessionStatus status,
            FeedbackStatus feedbackStatus,
            Instant endedAt
    ) {
        return interviewSessionRepository.save(InterviewSession.builder()
                .user(user)
                .position("백엔드 개발자")
                .selfIntroduction("자기소개")
                .experienceLevel(ExperienceLevel.JUNIOR)
                .interviewType(InterviewType.TECHNICAL)
                .totalQuestions(3)
                .currentQuestionNumber(3)
                .status(status)
                .feedbackStatus(feedbackStatus)
                .endedAt(endedAt)
                .build());
    }

    private InterviewFeedback saveFeedback(
            InterviewSession session,
            int overallScore,
            String nextPracticePoint
    ) {
        InterviewFeedback feedback = InterviewFeedback.create(
                session, feedbackResult(PlanTier.PRO), PlanTier.PRO);
        ReflectionTestUtils.setField(feedback, "overallScore", overallScore);
        ReflectionTestUtils.setField(feedback, "nextPracticePoint", nextPracticePoint);
        return interviewFeedbackRepository.saveAndFlush(feedback);
    }

    private void changeFeedbackCreatedAt(InterviewFeedback feedback, Instant createdAt) {
        jdbcTemplate.update(
                "UPDATE interview_feedback SET created_at = ? WHERE id = ?",
                createdAt,
                feedback.getId()
        );
        entityManager.clear();
    }
}
