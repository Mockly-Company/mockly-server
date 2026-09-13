package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.response.FeedbackDto;
import app.mockly.domain.interview.entity.InterviewFeedback;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.domain.product.service.CurrentSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackVisibilityService {
    private final CurrentSubscriptionService currentSubscriptionService;
    private final SubscriptionRepository subscriptionRepository;

    public FeedbackDto toResponse(UUID userId, InterviewFeedback feedback) {
        Subscription currentSubscription = currentSubscriptionService.getCurrentSubscription(userId);
        PlanTier currentTier = currentSubscription.getSubscriptionPlan().getProduct().getPlanTier();
        boolean exposeScores = shouldExposeScores(userId, feedback);
        boolean practiceAvailable = currentTier == PlanTier.PRO;
        return FeedbackDto.from(feedback, exposeScores, practiceAvailable);
    }

    private boolean shouldExposeScores(UUID userId, InterviewFeedback feedback) {
        if (feedback.getGeneratedTier() != PlanTier.FREE) {
            return true;
        }
        Instant generationStartedAt = feedback.getSession().getEndedAt();
        if (generationStartedAt == null) {
            generationStartedAt = feedback.getCreatedAt();
        }
        return subscriptionRepository.existsActivatedPaidSubscriptionAfter(userId, generationStartedAt);
    }
}
