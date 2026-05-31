package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.FeedbackContext;
import app.mockly.domain.interview.dto.FeedbackGenerationContext;
import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.event.FeedbackRequestedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class FeedbackGenerationHandler {

    private static final int MAX_RETRY = 3;

    private final FeedbackTransactionHelper txHelper;
    private final InterviewAiService interviewAiService;
    private final FeedbackSseManager feedbackSseManager;
    private final ObjectMapper objectMapper;

    @Async("feedbackExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FeedbackRequestedEvent event) {
        UUID sessionId = event.sessionId();
        UUID userId = event.userId();

        try {
            // TX2: GENERATING 설정 + AI 호출에 필요한 데이터 로딩
            Optional<FeedbackContext> feedbackContext = txHelper.markGeneratingAndLoadContext(sessionId, userId);
            if (feedbackContext.isEmpty()) {
                log.info("피드백 생성 작업 스킵: 이미 처리 중이거나 완료된 세션 sessionId={}", sessionId);
                return;
            }
            FeedbackContext ctx = feedbackContext.get();
            feedbackSseManager.send(sessionId, ctx.feedbackStatus());

            // NO TX: AI 호출 (재시도 포함)
            InterviewFeedbackResult result = callAiWithRetry(sessionId, ctx.context());

            // TX3-success: 피드백 저장 + 세션 완료
            String serializedExperts = serializeExperts(result);
            Optional<FeedbackStatus> completedStatus = txHelper.saveFeedbackAndComplete(sessionId, result, serializedExperts);
            if (completedStatus.isEmpty()) {
                log.info("피드백 완료 저장 스킵: 이미 완료 처리 중이거나 완료된 세션 sessionId={}", sessionId);
                return;
            }
            feedbackSseManager.send(sessionId, completedStatus.get());
            feedbackSseManager.complete(sessionId);
        } catch (FeedbackTransactionHelper.FeedbackCompletionException e) {
            log.error("피드백 완료 저장 실패 sessionId={}", sessionId, e);
        } catch (FeedbackInterruptedException e) {
            log.warn("피드백 생성 작업 인터럽트 sessionId={}", sessionId, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("피드백 생성 실패 sessionId={}", sessionId, e);
            FeedbackStatus failedStatus = FeedbackStatus.FAILED;
            try {
                // TX3-fail: 실패 마킹
                failedStatus = txHelper.markFailed(sessionId, e.getMessage());
            } catch (Exception inner) {
                log.error("피드백 실패 마킹 중 오류 sessionId={}", sessionId, inner);
            }
            if (failedStatus == FeedbackStatus.COMPLETED) {
                log.info("피드백 생성 실패 처리 스킵: 이미 완료된 세션 sessionId={}", sessionId);
                feedbackSseManager.send(sessionId, failedStatus);
                feedbackSseManager.complete(sessionId);
                return;
            }
            feedbackSseManager.send(sessionId, failedStatus, "피드백 생성에 실패했습니다.");
            feedbackSseManager.complete(sessionId);

            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private InterviewFeedbackResult callAiWithRetry(UUID sessionId, FeedbackGenerationContext ctx) {
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                return interviewAiService.generateFeedback(
                        ctx.history(), ctx.interviewType(), ctx.planTier());
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

    private String serializeExperts(InterviewFeedbackResult result) {
        try {
            return objectMapper.writeValueAsString(result.expertFeedbacks());
        } catch (Exception e) {
            throw new FeedbackTransactionHelper.FeedbackCompletionException("전문가 피드백 직렬화 실패", e);
        }
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
