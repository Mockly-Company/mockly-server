package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.ImprovementPracticeEvaluationContext;
import app.mockly.domain.interview.dto.ImprovementPracticeEvaluationResult;
import app.mockly.domain.interview.entity.ImprovementPracticeStatus;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.interview.event.ImprovementPracticeRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImprovementPracticeGenerationEventHandlerTest {

    @Mock
    private ImprovementPracticeStateService stateService;

    @Mock
    private InterviewAiService interviewAiService;

    @Mock
    private ImprovementPracticeSseManager sseManager;

    @InjectMocks
    private ImprovementPracticeGenerationEventHandler handler;

    private static ImprovementPracticeEvaluationContext sampleContext() {
        return new ImprovementPracticeEvaluationContext(
                1,
                "DB 병목을 해결한 경험을 설명해 주세요.",
                "인덱스를 추가했습니다.",
                "구체적 수치 보완",
                "개선 전후 정량 지표를 제시하세요.",
                "복합 인덱스 적용으로 p95 응답시간을 820ms에서 140ms로 단축했습니다.",
                InterviewType.TECHNICAL
        );
    }

    @Test
    @DisplayName("정상 평가 완료 시 complete 저장 후 COMPLETED SSE 이벤트를 전송한다")
    void handle_success_completesAndSendsSse() {
        Long practiceId = 10L;
        ImprovementPracticeEvaluationContext context = sampleContext();
        ImprovementPracticeEvaluationResult result = new ImprovementPracticeEvaluationResult(
                89,
                "정량 지표가 보완되어 설득력이 높아졌습니다.",
                "p95 지연 단축 수치를 명확히 제시해 기존 답변의 추상성을 해소했습니다.",
                "복합 인덱스 적용으로 p95 응답시간을 820ms에서 140ms로 단축했습니다."
        );

        given(stateService.loadEvaluationContext(practiceId)).willReturn(Optional.of(context));
        given(interviewAiService.evaluateImprovementPractice(context)).willReturn(result);
        given(stateService.complete(practiceId, result)).willReturn(true);

        handler.handle(new ImprovementPracticeRequestedEvent(practiceId));

        verify(stateService).complete(practiceId, result);
        verify(sseManager).send(practiceId, ImprovementPracticeStatus.COMPLETED);
        verify(sseManager).complete(practiceId);
        verify(stateService, never()).fail(any(), anyString());
    }

    @Test
    @DisplayName("첫 AI 호출이 실패하거나 잘못된 점수를 반환해도 재시도에 성공하면 COMPLETED 처리한다")
    void handle_retriesOnceAndSucceeds() {
        Long practiceId = 11L;
        ImprovementPracticeEvaluationContext context = sampleContext();
        ImprovementPracticeEvaluationResult invalid = new ImprovementPracticeEvaluationResult(
                0, "요약", "상세", "인용"
        );
        ImprovementPracticeEvaluationResult valid = new ImprovementPracticeEvaluationResult(
                85, "요약 코멘트", "구체적 비교 분석", "핵심 인용구"
        );

        given(stateService.loadEvaluationContext(practiceId)).willReturn(Optional.of(context));
        given(interviewAiService.evaluateImprovementPractice(context))
                .willReturn(invalid)
                .willReturn(valid);
        given(stateService.complete(practiceId, valid)).willReturn(true);

        handler.handle(new ImprovementPracticeRequestedEvent(practiceId));

        verify(interviewAiService, times(2)).evaluateImprovementPractice(context);
        verify(stateService).complete(practiceId, valid);
        verify(sseManager).send(practiceId, ImprovementPracticeStatus.COMPLETED);
        verify(sseManager).complete(practiceId);
    }

    @Test
    @DisplayName("AI 호출이 최대 재시도(2회) 모두 실패하면 별도 트랜잭션으로 FAILED 저장 후 FAILED SSE를 전송한다")
    void handle_allRetriesFail_marksFailedAndSendsFailedSse() {
        Long practiceId = 12L;
        ImprovementPracticeEvaluationContext context = sampleContext();

        given(stateService.loadEvaluationContext(practiceId)).willReturn(Optional.of(context));
        given(interviewAiService.evaluateImprovementPractice(context))
                .willThrow(new RuntimeException("OpenAI timeout"));
        given(stateService.fail(eq(practiceId), anyString())).willReturn(true);

        handler.handle(new ImprovementPracticeRequestedEvent(practiceId));

        verify(interviewAiService, times(2)).evaluateImprovementPractice(context);
        verify(stateService, never()).complete(any(), any());
        verify(stateService).fail(eq(practiceId), anyString());
        verify(sseManager).send(practiceId, ImprovementPracticeStatus.FAILED, "답변 개선 평가 생성에 실패했습니다.");
        verify(sseManager).complete(practiceId);
    }

    @Test
    @DisplayName("GENERATING 상태가 아닌 연습 이벤트는 AI 호출 없이 스킵한다")
    void handle_notGenerating_skips() {
        Long practiceId = 13L;
        given(stateService.loadEvaluationContext(practiceId)).willReturn(Optional.empty());

        handler.handle(new ImprovementPracticeRequestedEvent(practiceId));

        verify(interviewAiService, never()).evaluateImprovementPractice(any());
        verify(stateService, never()).complete(any(), any());
        verify(stateService, never()).fail(any(), anyString());
        verify(sseManager, never()).complete(any());
    }

    @Test
    @DisplayName("재시도 대기 중 인터럽트 발생 시 FAILED 마킹 없이 스레드 인터럽트 상태를 유지한다")
    void handle_interruptedDuringBackoff_preservesInterruptWithoutMarkingFailed() {
        Long practiceId = 14L;
        ImprovementPracticeEvaluationContext context = sampleContext();

        given(stateService.loadEvaluationContext(practiceId)).willReturn(Optional.of(context));
        given(interviewAiService.evaluateImprovementPractice(context))
                .willThrow(new RuntimeException("temporary failure"));

        try {
            Thread.currentThread().interrupt();
            handler.handle(new ImprovementPracticeRequestedEvent(practiceId));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(stateService, never()).fail(any(), anyString());
            verify(sseManager, never()).send(eq(practiceId), eq(ImprovementPracticeStatus.FAILED), anyString());
        } finally {
            Thread.interrupted();
        }
    }
}
