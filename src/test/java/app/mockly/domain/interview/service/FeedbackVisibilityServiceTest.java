package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.response.FeedbackDto;
import app.mockly.domain.interview.entity.InterviewFeedback;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.product.entity.*;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.domain.product.service.CurrentSubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static app.mockly.domain.interview.FeedbackTestFixtures.feedbackResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FeedbackVisibilityServiceTest {
    @Mock CurrentSubscriptionService currentSubscriptionService;
    @Mock SubscriptionRepository subscriptionRepository;
    @InjectMocks FeedbackVisibilityService service;

    @Test
    void hidesFreeScoresUntilAPaidSubscriptionStartsAfterTheFeedback() {
        UUID userId = UUID.randomUUID();
        InterviewFeedback feedback = feedback(PlanTier.FREE);
        given(currentSubscriptionService.getCurrentSubscription(userId)).willReturn(subscription(PlanTier.FREE));
        given(subscriptionRepository.existsActivatedPaidSubscriptionAfter(eq(userId), any(Instant.class)))
                .willReturn(false);

        FeedbackDto result = service.toResponse(userId, feedback);

        assertThat(result.scores()).isNull();
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(subscriptionRepository).existsActivatedPaidSubscriptionAfter(eq(userId), cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(result.improvements()).singleElement()
                .satisfies(improvement -> assertThat(improvement.practiceAvailable()).isFalse());
    }

    @Test
    void permanentlyExposesFreeScoresWhenAPaidSubscriptionStartedLater() {
        UUID userId = UUID.randomUUID();
        InterviewFeedback feedback = feedback(PlanTier.FREE);
        given(currentSubscriptionService.getCurrentSubscription(userId)).willReturn(subscription(PlanTier.FREE));
        given(subscriptionRepository.existsActivatedPaidSubscriptionAfter(eq(userId), any(Instant.class)))
                .willReturn(true);

        FeedbackDto result = service.toResponse(userId, feedback);

        assertThat(result.scores()).isNotNull();
        assertThat(result.strengths()).isEmpty();
        assertThat(result.improvements().getFirst().detail()).isNull();
    }

    @Test
    void keepsPaidGeneratedDetailsAfterDowngrade() {
        UUID userId = UUID.randomUUID();
        InterviewFeedback feedback = feedback(PlanTier.BASIC);
        given(currentSubscriptionService.getCurrentSubscription(userId)).willReturn(subscription(PlanTier.FREE));

        FeedbackDto result = service.toResponse(userId, feedback);

        assertThat(result.scores()).isNotNull();
        assertThat(result.strengths()).hasSize(3);
        assertThat(result.improvements()).allSatisfy(improvement ->
                assertThat(improvement.practiceAvailable()).isFalse());
    }

    @Test
    void marksPaidDetailsAvailableForPracticeOnlyWhenCurrentPlanIsPro() {
        UUID userId = UUID.randomUUID();
        InterviewFeedback feedback = feedback(PlanTier.BASIC);
        given(currentSubscriptionService.getCurrentSubscription(userId)).willReturn(subscription(PlanTier.PRO));

        FeedbackDto result = service.toResponse(userId, feedback);

        assertThat(result.improvements()).allSatisfy(improvement ->
                assertThat(improvement.practiceAvailable()).isTrue());
    }

    @Test
    void keepsPracticeAvailableDuringThePastDueGracePeriodForPro() {
        UUID userId = UUID.randomUUID();
        InterviewFeedback feedback = feedback(PlanTier.PRO);
        given(currentSubscriptionService.getCurrentSubscription(userId))
                .willReturn(subscription(PlanTier.PRO, SubscriptionStatus.PAST_DUE));

        FeedbackDto result = service.toResponse(userId, feedback);

        assertThat(result.improvements()).allSatisfy(improvement ->
                assertThat(improvement.practiceAvailable()).isTrue());
    }

    @Test
    void doesNotEnablePracticeWhenTheImprovementHasNoDetailEvenForPro() {
        UUID userId = UUID.randomUUID();
        InterviewFeedback feedback = feedback(PlanTier.BASIC);
        ReflectionTestUtils.setField(feedback.getImprovements().getFirst(), "detail", null);
        given(currentSubscriptionService.getCurrentSubscription(userId)).willReturn(subscription(PlanTier.PRO));

        FeedbackDto result = service.toResponse(userId, feedback);

        assertThat(result.improvements().getFirst().practiceAvailable()).isFalse();
        assertThat(result.improvements().subList(1, 3)).allSatisfy(improvement ->
                assertThat(improvement.practiceAvailable()).isTrue());
    }

    private InterviewFeedback feedback(PlanTier tier) {
        InterviewSession session = InterviewSession.builder()
                .endedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build();
        InterviewFeedback feedback = InterviewFeedback.create(
                session, feedbackResult(tier), tier);
        ReflectionTestUtils.setField(feedback, "createdAt", Instant.parse("2026-08-02T00:00:00Z"));
        return feedback;
    }

    private Subscription subscription(PlanTier tier) {
        return subscription(tier, SubscriptionStatus.ACTIVE);
    }

    private Subscription subscription(PlanTier tier, SubscriptionStatus status) {
        SubscriptionProduct product = SubscriptionProduct.builder().planTier(tier).build();
        SubscriptionPlan plan = SubscriptionPlan.builder().product(product).build();
        return Subscription.builder().subscriptionPlan(plan).status(status).build();
    }
}
