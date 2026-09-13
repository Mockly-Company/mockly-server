package app.mockly.domain.interview.service;

import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.interview.dto.request.SubmitAnswerRequest;
import app.mockly.domain.interview.entity.*;
import app.mockly.domain.interview.repository.InterviewFeedbackRepository;
import app.mockly.domain.interview.repository.InterviewMessageRepository;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionPlan;
import app.mockly.domain.product.entity.SubscriptionProduct;
import app.mockly.domain.product.service.CurrentSubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {
    @Mock InterviewSessionRepository interviewSessionRepository;
    @Mock InterviewMessageRepository interviewMessageRepository;
    @Mock InterviewFeedbackRepository interviewFeedbackRepository;
    @Mock UserRepository userRepository;
    @Mock InterviewAiService interviewAiService;
    @Mock InterviewCreationService interviewCreationService;
    @Mock WeeklyQuotaService weeklyQuotaService;
    @Mock FeedbackVisibilityService feedbackVisibilityService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock CurrentSubscriptionService currentSubscriptionService;

    @InjectMocks InterviewService interviewService;

    @Test
    void finalAnswerSnapshotsTheCurrentPlanForFeedbackGeneration() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = InterviewSession.builder()
                .id(sessionId)
                .status(InterviewSessionStatus.IN_PROGRESS)
                .interviewType(InterviewType.TECHNICAL)
                .currentQuestionNumber(3)
                .totalQuestions(3)
                .build();
        SubscriptionProduct product = SubscriptionProduct.builder().planTier(PlanTier.PRO).build();
        SubscriptionPlan plan = SubscriptionPlan.builder().product(product).build();
        Subscription subscription = Subscription.builder().subscriptionPlan(plan).build();
        given(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(currentSubscriptionService.getCurrentSubscription(userId)).willReturn(subscription);

        interviewService.submitAnswer(userId, sessionId, new SubmitAnswerRequest("마지막 답변"));

        assertThat(session.getFeedbackGenerationTier()).isEqualTo(PlanTier.PRO);
    }
}
