package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.ImprovementPracticeEvaluationContext;
import app.mockly.domain.interview.dto.ImprovementPracticeEvaluationResult;
import app.mockly.domain.interview.entity.ImprovementPracticeStatus;
import app.mockly.domain.interview.event.ImprovementPracticeRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImprovementPracticeGenerationEventHandler {

    private static final int MAX_RETRY = 2;

    private final ImprovementPracticeStateService stateService;
    private final InterviewAiService interviewAiService;
    private final ImprovementPracticeSseManager sseManager;

    @Async("feedbackExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ImprovementPracticeRequestedEvent event) {
        Long practiceId = event.practiceId();

        Optional<ImprovementPracticeEvaluationContext> contextOpt;
        try {
            contextOpt = stateService.loadEvaluationContext(practiceId);
        } catch (Exception e) {
            log.error("개선 연습 평가 컨텍스트 로딩 실패 practiceId={}", practiceId, e);
            return;
        }

        if (contextOpt.isEmpty()) {
            log.info("개선 연습 평가 작업 스킵: GENERATING 상태가 아님 practiceId={}", practiceId);
            return;
        }

        try {
            ImprovementPracticeEvaluationResult result = callAiWithRetry(practiceId, contextOpt.get());
            if (stateService.complete(practiceId, result)) {
                sseManager.send(practiceId, ImprovementPracticeStatus.COMPLETED);
                sseManager.complete(practiceId);
            }
        } catch (PracticeInterruptedException e) {
            log.warn("개선 연습 평가 인터럽트 practiceId={}", practiceId, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            handleEvaluationFailure(practiceId, e);
        }
    }

    private void handleEvaluationFailure(Long practiceId, Exception exception) {
        log.error("개선 연습 비교 평가 실패 practiceId={}", practiceId, exception);
        try {
            if (stateService.fail(practiceId, exception.getMessage())) {
                sseManager.send(practiceId, ImprovementPracticeStatus.FAILED, "답변 개선 평가 생성에 실패했습니다.");
                sseManager.complete(practiceId);
            }
        } catch (Exception failEx) {
            log.error("개선 연습 실패 상태 저장 중 오류 practiceId={}", practiceId, failEx);
        }
    }

    private ImprovementPracticeEvaluationResult callAiWithRetry(
            Long practiceId,
            ImprovementPracticeEvaluationContext context
    ) {
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                ImprovementPracticeEvaluationResult result = interviewAiService.evaluateImprovementPractice(context);
                validateResult(result);
                return result;
            } catch (Exception e) {
                if (hasInterruptedCause(e)) {
                    Thread.currentThread().interrupt();
                    throw new PracticeInterruptedException("개선 연습 평가 중 인터럽트 발생", e);
                }
                lastException = e;
                log.warn("AI 개선 연습 평가 실패 practiceId={} (attempt={}/{}): {}",
                        practiceId, attempt + 1, MAX_RETRY, e.getMessage());
                if (attempt < MAX_RETRY - 1) {
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new PracticeInterruptedException("개선 연습 평가 대기 중 인터럽트 발생", ie);
                    }
                }
            }
        }
        throw new RuntimeException("AI 개선 연습 평가 " + MAX_RETRY + "회 모두 실패", lastException);
    }

    private void validateResult(ImprovementPracticeEvaluationResult result) {
        if (result == null) {
            throw new IllegalStateException("AI 평가 응답이 비어 있습니다.");
        }
        if (result.score() < 1 || result.score() > 100) {
            throw new IllegalStateException("AI 평가 점수 범위 오류: " + result.score());
        }
        if (result.summary() == null || result.summary().isBlank()
                || result.detail() == null || result.detail().isBlank()) {
            throw new IllegalStateException("AI 평가 요약 또는 상세 분석이 비어 있습니다.");
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

    private static class PracticeInterruptedException extends RuntimeException {
        private PracticeInterruptedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
