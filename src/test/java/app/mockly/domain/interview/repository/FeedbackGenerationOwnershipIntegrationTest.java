package app.mockly.domain.interview.repository;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewSessionStatus;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.product.entity.PlanTier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class FeedbackGenerationOwnershipIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Test
    void staleWorkerCannotCompleteAfterNewWorkerStarts() {
        User user = userRepository.saveAndFlush(User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("feedback-owner-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com")
                .name("피드백 작업 소유권 테스트")
                .build());
        InterviewSession session = InterviewSession.create(
                user,
                "백엔드 개발자",
                ExperienceLevel.JUNIOR,
                InterviewType.TECHNICAL,
                3,
                "자기소개");
        session.startFeedbackGeneration(PlanTier.FREE);
        session = interviewSessionRepository.saveAndFlush(session);

        UUID firstTaskId = UUID.randomUUID();
        assertThat(interviewSessionRepository.markFeedbackGeneratingIfPending(
                session.getId(),
                FeedbackStatus.PENDING,
                FeedbackStatus.GENERATING,
                firstTaskId,
                Instant.now()))
                .isEqualTo(1);

        assertThat(interviewSessionRepository.requeueStaleGenerating(
                session.getId(),
                firstTaskId,
                FeedbackStatus.GENERATING,
                FeedbackStatus.PENDING,
                Instant.now().plusSeconds(60),
                Instant.now()))
                .isEqualTo(1);

        UUID secondTaskId = UUID.randomUUID();
        assertThat(interviewSessionRepository.markFeedbackGeneratingIfPending(
                session.getId(),
                FeedbackStatus.PENDING,
                FeedbackStatus.GENERATING,
                secondTaskId,
                Instant.now()))
                .isEqualTo(1);

        assertThat(interviewSessionRepository.failFeedbackIfOwned(
                session.getId(),
                firstTaskId,
                FeedbackStatus.GENERATING,
                FeedbackStatus.FAILED,
                "late worker failure",
                Instant.now()))
                .isZero();

        InterviewSession stillOwnedBySecondWorker = interviewSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(stillOwnedBySecondWorker.getFeedbackStatus()).isEqualTo(FeedbackStatus.GENERATING);
        assertThat(stillOwnedBySecondWorker.getFeedbackGenerationTaskId()).isEqualTo(secondTaskId);

        Instant firstCompletionTime = Instant.now();
        assertThat(interviewSessionRepository.completeFeedbackIfOwned(
                session.getId(),
                firstTaskId,
                FeedbackStatus.GENERATING,
                FeedbackStatus.COMPLETED,
                InterviewSessionStatus.COMPLETED,
                firstCompletionTime,
                firstCompletionTime))
                .isZero();

        Instant secondCompletionTime = Instant.now();
        assertThat(interviewSessionRepository.completeFeedbackIfOwned(
                session.getId(),
                secondTaskId,
                FeedbackStatus.GENERATING,
                FeedbackStatus.COMPLETED,
                InterviewSessionStatus.COMPLETED,
                secondCompletionTime,
                secondCompletionTime))
                .isEqualTo(1);

        InterviewSession completed = interviewSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(completed.getFeedbackStatus()).isEqualTo(FeedbackStatus.COMPLETED);
        assertThat(completed.getFeedbackGenerationTaskId()).isNull();
        assertThat(completed.getCompletedAt()).isEqualTo(secondCompletionTime);
    }
}
