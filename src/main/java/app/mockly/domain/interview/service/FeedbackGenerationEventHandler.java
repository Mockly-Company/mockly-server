package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.FeedbackContext;
import app.mockly.domain.interview.dto.FeedbackGenerationContext;
import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.event.FeedbackRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackGenerationEventHandler {

    private static final int MAX_RETRY = 3;

    private final FeedbackGenerationStateService stateService;
    private final InterviewAiService interviewAiService;
    private final FeedbackSseManager feedbackSseManager;
    private final FeedbackResultValidator feedbackResultValidator;

    @Async("feedbackExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FeedbackRequestedEvent event) {
        UUID sessionId = event.sessionId();

        Optional<FeedbackContext> feedbackContext;
        try {
            // TX2: GENERATING 설정 + AI 호출에 필요한 데이터 로딩
            feedbackContext = stateService.start(sessionId);
        } catch (Exception e) {
            log.error("피드백 생성 작업 시작 실패 sessionId={}", sessionId, e);
            return;
        }

        if (feedbackContext.isEmpty()) {
            log.info("피드백 생성 작업 스킵: 이미 처리 중이거나 완료된 세션 sessionId={}", sessionId);
            return;
        }

        processOwnedFeedback(sessionId, feedbackContext.get());
    }

    private void processOwnedFeedback(UUID sessionId, FeedbackContext feedbackContext) {
        try {
            feedbackSseManager.send(sessionId, feedbackContext.feedbackStatus());

            // NO TX: AI 호출 (재시도 포함)
            InterviewFeedbackResult result = callAiWithRetry(sessionId, feedbackContext.context());

            completeOwnedFeedback(sessionId, feedbackContext.taskId(), result);
        } catch (FeedbackGenerationStateService.FeedbackCompletionException e) {
            log.error("피드백 완료 저장 실패 sessionId={}", sessionId, e);
        } catch (FeedbackInterruptedException e) {
            log.warn("피드백 생성 작업 인터럽트 sessionId={}", sessionId, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            handleGenerationFailure(sessionId, feedbackContext.taskId(), e);
        }
    }

    private void completeOwnedFeedback(UUID sessionId, UUID taskId, InterviewFeedbackResult result) {
        // TX3-success: 피드백 저장 + 세션 완료
        Optional<FeedbackStatus> completedStatus = stateService.complete(sessionId, taskId, result);
        if (completedStatus.isEmpty()) {
            log.info("피드백 완료 저장 스킵: 이미 완료 처리 중이거나 완료된 세션 sessionId={}", sessionId);
            return;
        }
        feedbackSseManager.send(sessionId, completedStatus.get());
        feedbackSseManager.complete(sessionId);
    }

    private void handleGenerationFailure(UUID sessionId, UUID taskId, Exception exception) {
        log.error("피드백 생성 실패 sessionId={}", sessionId, exception);

        Optional<FeedbackStatus> failedStatus;
        try {
            // TX3-fail: 실패 마킹
            failedStatus = stateService.fail(sessionId, taskId, exception.getMessage());
        } catch (Exception failException) {
            log.error("피드백 실패 마킹 중 오류 sessionId={}", sessionId, failException);
            return;
        }

        if (failedStatus.isEmpty()) {
            log.info("피드백 실패 처리 스킵: 작업 소유권을 잃은 세션 sessionId={}, taskId={}", sessionId, taskId);
            return;
        }
        feedbackSseManager.send(sessionId, failedStatus.get(), "피드백 생성에 실패했습니다.");
        feedbackSseManager.complete(sessionId);

        if (exception.getCause() instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private InterviewFeedbackResult callAiWithRetry(UUID sessionId, FeedbackGenerationContext ctx) {
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                InterviewFeedbackResult result = interviewAiService.generateFeedback(
                        ctx.history(), ctx.interviewType(), ctx.planTier());
                feedbackResultValidator.validate(result, ctx.planTier(), ctx.totalQuestions());
                return result;
            } catch (Exception e) {
                if (hasInterruptedCause(e)) {
                    Thread.currentThread().interrupt();
                    throw new FeedbackInterruptedException("피드백 생성 중 인터럽트 발생", e);
                }
                lastException = e;
                log.warn("AI 피드백 생성 실패 sessionId={} (attempt={}/{}): {}",
                        sessionId, attempt + 1, MAX_RETRY, e.getMessage());
                if (attempt < MAX_RETRY - 1) {
                    try {
                        Thread.sleep(1000L * (1L << attempt)); // 1s → 2s
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new FeedbackInterruptedException("피드백 생성 중 인터럽트 발생", ie);
                    }
                }
            }
        }
        throw new RuntimeException("AI 피드백 생성 " + MAX_RETRY + "회 모두 실패", lastException);
    }

    private boolean hasInterruptedCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static class FeedbackInterruptedException extends RuntimeException {
        private FeedbackInterruptedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
