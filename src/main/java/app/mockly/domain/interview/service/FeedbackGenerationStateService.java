package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.FeedbackContext;
import app.mockly.domain.interview.dto.FeedbackGenerationContext;
import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewFeedback;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewSessionStatus;
import app.mockly.domain.interview.repository.InterviewFeedbackRepository;
import app.mockly.domain.interview.repository.InterviewMessageRepository;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackGenerationStateService {

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewFeedbackRepository interviewFeedbackRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<FeedbackContext> start(UUID sessionId) {
        UUID taskId = UUID.randomUUID();
        int updatedRows = interviewSessionRepository.markFeedbackGeneratingIfPending(
                sessionId, FeedbackStatus.PENDING, FeedbackStatus.GENERATING, taskId, Instant.now());
        if (updatedRows == 0) {
            if (!interviewSessionRepository.existsById(sessionId)) {
                throw new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND);
            }
            // 다른 작업자가 선점하거나 이미 처리되어 작업 소유권을 얻지 못한 경우
            return Optional.empty();
        }

        InterviewSession session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));

        FeedbackGenerationContext genCtx = new FeedbackGenerationContext(
                interviewMessageRepository.findBySessionIdOrderByIdAsc(sessionId),
                session.getInterviewType(),
                session.getFeedbackGenerationTier(),
                session.getTotalQuestions()
        );
        return Optional.of(new FeedbackContext(genCtx, session.getFeedbackStatus(), taskId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<FeedbackStatus> complete(UUID sessionId, UUID taskId, InterviewFeedbackResult result) {
        Instant now = Instant.now();
        int updatedRows = interviewSessionRepository.completeFeedbackIfOwned(
                sessionId,
                taskId,
                FeedbackStatus.GENERATING,
                FeedbackStatus.COMPLETED,
                InterviewSessionStatus.COMPLETED,
                now,
                now);
        if (updatedRows == 0) {
            return Optional.empty();
        }

        try {
            InterviewSession session = interviewSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND));
            interviewFeedbackRepository.saveAndFlush(InterviewFeedback.create(
                    session, result, session.getFeedbackGenerationTier()));
            return Optional.of(FeedbackStatus.COMPLETED);
        } catch (Exception e) {
            throw new FeedbackCompletionException("피드백 완료 저장 실패", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<FeedbackStatus> fail(UUID sessionId, UUID taskId, String reason) {
        String failReason = truncateFailReason(reason);
        int updatedRows = interviewSessionRepository.failFeedbackIfOwned(
                sessionId,
                taskId,
                FeedbackStatus.GENERATING,
                FeedbackStatus.FAILED,
                failReason,
                Instant.now());
        if (updatedRows == 0) {
            return Optional.empty();
        }
        return Optional.of(FeedbackStatus.FAILED);
    }

    private String truncateFailReason(String reason) {
        if (reason == null || reason.length() <= 500) {
            return reason;
        }
        return reason.substring(0, 500);
    }

    public static class FeedbackCompletionException extends RuntimeException {
        public FeedbackCompletionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
