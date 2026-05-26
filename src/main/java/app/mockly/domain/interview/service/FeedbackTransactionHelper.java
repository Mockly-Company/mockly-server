package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.FeedbackContext;
import app.mockly.domain.interview.dto.FeedbackGenerationContext;
import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewFeedback;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.repository.InterviewFeedbackRepository;
import app.mockly.domain.interview.repository.InterviewMessageRepository;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.entity.SubscriptionStatus;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackTransactionHelper {

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewFeedbackRepository interviewFeedbackRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FeedbackContext markGeneratingAndLoadContext(UUID sessionId, UUID userId) {
        InterviewSession session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));
        session.markFeedbackGenerating();

        PlanTier planTier = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .map(sub -> sub.getSubscriptionPlan().getProduct().getPlanTier())
                .orElse(PlanTier.FREE);
        FeedbackGenerationContext genCtx = new FeedbackGenerationContext(
                interviewMessageRepository.findBySessionIdOrderByIdAsc(sessionId),
                session.getInterviewType(),
                planTier
        );
        return new FeedbackContext(genCtx, session.getFeedbackStatus());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FeedbackStatus saveFeedbackAndComplete(UUID sessionId, InterviewFeedbackResult result, String serializedExperts) {
        InterviewSession session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));

        interviewFeedbackRepository.save(InterviewFeedback.create(
                session,
                result.overallScore(),
                serializedExperts,
                result.strengths(),
                result.improvements(),
                result.detailedAnalysis()
        ));

        session.complete();
        return session.getFeedbackStatus();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FeedbackStatus markFailed(UUID sessionId, String reason) {
        return interviewSessionRepository.findById(sessionId)
                .map(session -> {
                    session.markFeedbackFailed(reason);
                    return session.getFeedbackStatus();
                })
                .orElse(FeedbackStatus.FAILED);
    }
}
