package app.mockly.domain.interview.service;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.interview.dto.WeeklyQuotaContext;
import app.mockly.domain.interview.dto.response.GetInterviewOverviewResponse;
import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewSessionStatus;
import app.mockly.domain.interview.repository.InterviewFeedbackRepository;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.entity.SubscriptionProduct;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InterviewOverviewServiceTest {

    @Mock UserRepository userRepository;
    @Mock WeeklyQuotaService weeklyQuotaService;
    @Mock InterviewSessionRepository interviewSessionRepository;
    @Mock InterviewFeedbackRepository interviewFeedbackRepository;
    @InjectMocks InterviewOverviewService interviewOverviewService;

    @Test
    void summarizesLastAnswerSubmittedSessionsWithinTheCurrentPersonalWeek() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().build();
        WeeklyQuotaContext context = context(PlanTier.FREE);
        InterviewSession pending = session(
                Instant.parse("2026-08-25T01:00:00Z"),
                Instant.parse("2026-08-25T01:10:00Z"),
                InterviewSessionStatus.FEEDBACK_PENDING,
                FeedbackStatus.GENERATING);
        InterviewSession completed = session(
                Instant.parse("2026-08-26T01:00:00Z"),
                Instant.parse("2026-08-26T01:30:00Z"),
                InterviewSessionStatus.COMPLETED,
                FeedbackStatus.COMPLETED);
        InterviewSession abandonedRecent = session(
                Instant.parse("2026-08-27T01:00:00Z"),
                Instant.parse("2026-08-27T01:05:00Z"),
                InterviewSessionStatus.ABANDONED,
                null);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(weeklyQuotaService.calculateCurrentQuotaContext(eq(user), any(Instant.class))).willReturn(context);
        given(interviewSessionRepository.findLastAnswerSubmittedSessionsInPeriod(
                eq(userId), any(Instant.class), any(Instant.class)))
                .willReturn(List.of(pending, completed));
        given(interviewSessionRepository.findTop3ByUserIdAndEndedAtIsNotNullOrderByEndedAtDesc(userId))
                .willReturn(List.of(abandonedRecent, completed, pending));
        given(interviewFeedbackRepository.findRecentOverallScores(eq(userId), any(Pageable.class)))
                .willReturn(List.of(82, 78));

        GetInterviewOverviewResponse response = interviewOverviewService.getOverview(userId);

        assertThat(response.summary().completedCount()).isEqualTo(2);
        assertThat(response.summary().totalPracticeSeconds()).isEqualTo(2_400);
        assertThat(response.score().latest()).isEqualTo(82);
        assertThat(response.score().change()).isEqualTo(4);
        assertThat(response.recentInterviews()).hasSize(3);
        assertThat(response.recentInterviews().getFirst().status()).isEqualTo(InterviewSessionStatus.ABANDONED);
        assertThat(response.nextPracticePoint()).isNull();
        verify(interviewFeedbackRepository, never())
                .findRecentPracticePoints(eq(userId), any(Pageable.class));

        ArgumentCaptor<Instant> periodStart = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> periodEnd = ArgumentCaptor.forClass(Instant.class);
        verify(interviewSessionRepository).findLastAnswerSubmittedSessionsInPeriod(
                eq(userId), periodStart.capture(), periodEnd.capture());
        assertThat(periodStart.getValue()).isEqualTo(Instant.parse("2026-08-23T15:00:00Z"));
        assertThat(periodEnd.getValue()).isEqualTo(Instant.parse("2026-08-30T15:00:00Z"));
    }

    @Test
    void exposesTheMostRecentAvailablePracticePointOnlyForPro() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(weeklyQuotaService.calculateCurrentQuotaContext(eq(user), any(Instant.class)))
                .willReturn(context(PlanTier.PRO));
        given(interviewSessionRepository.findLastAnswerSubmittedSessionsInPeriod(
                eq(userId), any(Instant.class), any(Instant.class))).willReturn(List.of());
        given(interviewSessionRepository.findTop3ByUserIdAndEndedAtIsNotNullOrderByEndedAtDesc(userId))
                .willReturn(List.of());
        given(interviewFeedbackRepository.findRecentOverallScores(eq(userId), any(Pageable.class)))
                .willReturn(List.of());
        given(interviewFeedbackRepository.findRecentPracticePoints(eq(userId), any(Pageable.class)))
                .willReturn(List.of("결론부터 답변하기"));

        GetInterviewOverviewResponse response = interviewOverviewService.getOverview(userId);

        assertThat(response.nextPracticePoint()).isEqualTo("결론부터 답변하기");
        assertThat(response.score().latest()).isNull();
        assertThat(response.recentInterviews()).isEmpty();
    }

    private WeeklyQuotaContext context(PlanTier tier) {
        SubscriptionProduct product = SubscriptionProduct.builder().planTier(tier).build();
        return WeeklyQuotaContext.of(product, LocalDate.of(2026, 8, 24), true);
    }

    private InterviewSession session(
            Instant createdAt,
            Instant endedAt,
            InterviewSessionStatus status,
            FeedbackStatus feedbackStatus
    ) {
        InterviewSession session = InterviewSession.builder()
                .id(UUID.randomUUID())
                .position("백엔드 개발자")
                .totalQuestions(3)
                .status(status)
                .endedAt(endedAt)
                .feedbackStatus(feedbackStatus)
                .build();
        ReflectionTestUtils.setField(session, "createdAt", createdAt);
        return session;
    }
}
