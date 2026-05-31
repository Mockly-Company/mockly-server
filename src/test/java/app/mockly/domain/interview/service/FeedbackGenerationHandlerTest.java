package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.FeedbackContext;
import app.mockly.domain.interview.dto.FeedbackGenerationContext;
import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.interview.event.FeedbackRequestedEvent;
import app.mockly.domain.product.entity.PlanTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FeedbackGenerationHandlerTest {

    @Mock
    private FeedbackTransactionHelper txHelper;

    @Mock
    private InterviewAiService interviewAiService;

    @Mock
    private FeedbackSseManager feedbackSseManager;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private FeedbackGenerationHandler handler;

    @Test
    @DisplayName("중복 이벤트는 AI 호출과 실패 마킹 없이 skip한다")
    void handle_duplicateEvent_skipsWithoutFailure() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        given(txHelper.markGeneratingAndLoadContext(sessionId, userId)).willReturn(Optional.empty());

        handler.handle(new FeedbackRequestedEvent(sessionId, userId));

        verify(interviewAiService, never()).generateFeedback(any(), any(), any());
        verify(txHelper, never()).saveFeedbackAndComplete(any(), any(), anyString());
        verify(txHelper, never()).markFailed(any(), anyString());
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.FAILED), anyString());
    }

    @Test
    @DisplayName("같은 이벤트가 두 번 들어오면 작업권을 얻은 첫 요청만 AI를 호출한다")
    void handle_sameEventTwice_callsAiOnce() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);
        InterviewFeedbackResult result = new InterviewFeedbackResult(
                80,
                List.of(new InterviewFeedbackResult.ExpertFeedback("기술 면접관", 80, "좋습니다.")),
                "강점",
                "개선점",
                null
        );

        given(txHelper.markGeneratingAndLoadContext(sessionId, userId))
                .willReturn(Optional.of(feedbackContext))
                .willReturn(Optional.empty());
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willReturn(result);
        given(txHelper.saveFeedbackAndComplete(eq(sessionId), eq(result), anyString()))
                .willReturn(Optional.of(FeedbackStatus.COMPLETED));

        FeedbackRequestedEvent event = new FeedbackRequestedEvent(sessionId, userId);
        handler.handle(event);
        handler.handle(event);

        verify(interviewAiService, times(1)).generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE);
        verify(txHelper, times(1)).saveFeedbackAndComplete(eq(sessionId), eq(result), anyString());
        verify(txHelper, never()).markFailed(any(), anyString());
    }

    @Test
    @DisplayName("늦은 완료 worker는 완료/실패 SSE 없이 skip한다")
    void handle_lateCompletionWorker_skipsWithoutSse() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);
        InterviewFeedbackResult result = new InterviewFeedbackResult(
                80,
                List.of(new InterviewFeedbackResult.ExpertFeedback("기술 면접관", 80, "좋습니다.")),
                "강점",
                "개선점",
                null
        );

        given(txHelper.markGeneratingAndLoadContext(sessionId, userId)).willReturn(Optional.of(feedbackContext));
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willReturn(result);
        given(txHelper.saveFeedbackAndComplete(eq(sessionId), eq(result), anyString()))
                .willReturn(Optional.empty());

        handler.handle(new FeedbackRequestedEvent(sessionId, userId));

        verify(txHelper, never()).markFailed(any(), anyString());
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.COMPLETED));
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.FAILED), anyString());
        verify(feedbackSseManager, never()).complete(sessionId);
    }

    @Test
    @DisplayName("피드백 저장 실패는 FAILED 마킹이나 SSE 없이 stale recovery에 맡긴다")
    void handle_feedbackSaveFailure_doesNotMarkFailed() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);
        InterviewFeedbackResult result = new InterviewFeedbackResult(
                80,
                List.of(new InterviewFeedbackResult.ExpertFeedback("기술 면접관", 80, "좋습니다.")),
                "강점",
                "개선점",
                null
        );

        given(txHelper.markGeneratingAndLoadContext(sessionId, userId)).willReturn(Optional.of(feedbackContext));
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willReturn(result);
        given(txHelper.saveFeedbackAndComplete(eq(sessionId), eq(result), anyString()))
                .willThrow(new FeedbackTransactionHelper.FeedbackCompletionException("저장 실패", new RuntimeException()));

        handler.handle(new FeedbackRequestedEvent(sessionId, userId));

        verify(txHelper, never()).markFailed(any(), anyString());
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.COMPLETED));
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.FAILED), anyString());
        verify(feedbackSseManager, never()).complete(sessionId);
    }

    @Test
    @DisplayName("AI 실패 처리 중 이미 완료된 세션이면 실패 메시지 없이 완료 상태만 전송한다")
    void handle_aiFailureButAlreadyCompleted_sendsCompletedWithoutFailureMessage() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);

        given(txHelper.markGeneratingAndLoadContext(sessionId, userId)).willReturn(Optional.of(feedbackContext));
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willThrow(new RuntimeException("ai error"));
        given(txHelper.markFailed(eq(sessionId), anyString())).willReturn(FeedbackStatus.COMPLETED);

        handler.handle(new FeedbackRequestedEvent(sessionId, userId));

        verify(feedbackSseManager).send(sessionId, FeedbackStatus.COMPLETED);
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.COMPLETED), anyString());
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.FAILED), anyString());
        verify(feedbackSseManager).complete(sessionId);
    }

    @Test
    @DisplayName("retry backoff 중 인터럽트가 발생하면 실패 마킹과 SSE 없이 중단한다")
    void handle_interruptedDuringRetryBackoff_doesNotMarkFailed() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);

        given(txHelper.markGeneratingAndLoadContext(sessionId, userId)).willReturn(Optional.of(feedbackContext));
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willThrow(new RuntimeException("temporary ai error"));

        try {
            Thread.currentThread().interrupt();

            handler.handle(new FeedbackRequestedEvent(sessionId, userId));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(txHelper, never()).markFailed(any(), anyString());
            verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.FAILED), anyString());
            verify(feedbackSseManager, never()).complete(sessionId);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("AI 예외 cause chain에 인터럽트가 있으면 실패 마킹과 SSE 없이 중단한다")
    void handle_interruptedCause_doesNotMarkFailed() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);

        given(txHelper.markGeneratingAndLoadContext(sessionId, userId)).willReturn(Optional.of(feedbackContext));
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willThrow(new RuntimeException("wrapped", new RuntimeException(new InterruptedException())));

        try {
            handler.handle(new FeedbackRequestedEvent(sessionId, userId));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(txHelper, never()).markFailed(any(), anyString());
            verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.FAILED), anyString());
            verify(feedbackSseManager, never()).complete(sessionId);
        } finally {
            Thread.interrupted();
        }
    }
}
