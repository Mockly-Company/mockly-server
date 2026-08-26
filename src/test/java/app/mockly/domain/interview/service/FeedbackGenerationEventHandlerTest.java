package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.FeedbackContext;
import app.mockly.domain.interview.dto.FeedbackGenerationContext;
import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.interview.event.FeedbackRequestedEvent;
import app.mockly.domain.product.entity.PlanTier;
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
import static app.mockly.domain.interview.FeedbackTestFixtures.feedbackResult;

@ExtendWith(MockitoExtension.class)
class FeedbackGenerationEventHandlerTest {

    @Mock
    private FeedbackGenerationStateService stateService;

    @Mock
    private InterviewAiService interviewAiService;

    @Mock
    private FeedbackSseManager feedbackSseManager;

    @Spy
    private FeedbackResultValidator feedbackResultValidator = new FeedbackResultValidator();

    @InjectMocks
    private FeedbackGenerationEventHandler handler;

    @Test
    @DisplayName("중복 이벤트는 AI 호출과 실패 마킹 없이 skip한다")
    void handle_duplicateEvent_skipsWithoutFailure() {
        UUID sessionId = UUID.randomUUID();

        given(stateService.start(sessionId)).willReturn(Optional.empty());

        handler.handle(new FeedbackRequestedEvent(sessionId));

        verify(interviewAiService, never()).generateFeedback(any(), any(), any());
        verify(stateService, never()).complete(any(), any());
        verify(stateService, never()).fail(any(), anyString());
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.FAILED), anyString());
    }

    @Test
    @DisplayName("같은 이벤트가 두 번 들어오면 작업권을 얻은 첫 요청만 AI를 호출한다")
    void handle_sameEventTwice_callsAiOnce() {
        UUID sessionId = UUID.randomUUID();
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE, 3);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);
        InterviewFeedbackResult result = feedbackResult(PlanTier.FREE);

        given(stateService.start(sessionId))
                .willReturn(Optional.of(feedbackContext))
                .willReturn(Optional.empty());
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willReturn(result);
        given(stateService.complete(eq(sessionId), eq(result)))
                .willReturn(Optional.of(FeedbackStatus.COMPLETED));

        FeedbackRequestedEvent event = new FeedbackRequestedEvent(sessionId);
        handler.handle(event);
        handler.handle(event);

        verify(interviewAiService, times(1)).generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE);
        verify(stateService, times(1)).complete(eq(sessionId), eq(result));
        verify(stateService, never()).fail(any(), anyString());
    }

    @Test
    @DisplayName("늦은 완료 worker는 완료/실패 SSE 없이 skip한다")
    void handle_lateCompletionWorker_skipsWithoutSse() {
        UUID sessionId = UUID.randomUUID();
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE, 3);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);
        InterviewFeedbackResult result = feedbackResult(PlanTier.FREE);

        given(stateService.start(sessionId)).willReturn(Optional.of(feedbackContext));
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willReturn(result);
        given(stateService.complete(eq(sessionId), eq(result)))
                .willReturn(Optional.empty());

        handler.handle(new FeedbackRequestedEvent(sessionId));

        verify(stateService, never()).fail(any(), anyString());
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.COMPLETED));
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.FAILED), anyString());
        verify(feedbackSseManager, never()).complete(sessionId);
    }

    @Test
    @DisplayName("피드백 저장 실패는 FAILED 마킹이나 SSE 없이 stale recovery에 맡긴다")
    void handle_feedbackSaveFailure_doesNotMarkFailed() {
        UUID sessionId = UUID.randomUUID();
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE, 3);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);
        InterviewFeedbackResult result = feedbackResult(PlanTier.FREE);

        given(stateService.start(sessionId)).willReturn(Optional.of(feedbackContext));
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willReturn(result);
        given(stateService.complete(eq(sessionId), eq(result)))
                .willThrow(new FeedbackGenerationStateService.FeedbackCompletionException("저장 실패", new RuntimeException()));

        handler.handle(new FeedbackRequestedEvent(sessionId));

        verify(stateService, never()).fail(any(), anyString());
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.COMPLETED));
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.FAILED), anyString());
        verify(feedbackSseManager, never()).complete(sessionId);
    }

    @Test
    @DisplayName("AI 실패 처리 중 이미 완료된 세션이면 실패 메시지 없이 완료 상태만 전송한다")
    void handle_aiFailureButAlreadyCompleted_sendsCompletedWithoutFailureMessage() {
        UUID sessionId = UUID.randomUUID();
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE, 3);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);

        given(stateService.start(sessionId)).willReturn(Optional.of(feedbackContext));
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willThrow(new RuntimeException("ai error"));
        given(stateService.fail(eq(sessionId), anyString())).willReturn(FeedbackStatus.COMPLETED);

        handler.handle(new FeedbackRequestedEvent(sessionId));

        verify(feedbackSseManager).send(sessionId, FeedbackStatus.COMPLETED);
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.COMPLETED), anyString());
        verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.FAILED), anyString());
        verify(feedbackSseManager).complete(sessionId);
    }

    @Test
    @DisplayName("계약을 위반한 AI 결과는 저장하지 않고 다시 생성한다")
    void handle_invalidResult_retriesAndSavesOnlyValidResult() {
        UUID sessionId = UUID.randomUUID();
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE, 3);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);
        InterviewFeedbackResult valid = feedbackResult(PlanTier.FREE);
        InterviewFeedbackResult invalid = new InterviewFeedbackResult(
                0, valid.coachBrief(), valid.scores(), valid.strengths(), valid.improvements(), null);

        given(stateService.start(sessionId)).willReturn(Optional.of(feedbackContext));
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willReturn(invalid)
                .willReturn(valid);
        given(stateService.complete(sessionId, valid))
                .willReturn(Optional.of(FeedbackStatus.COMPLETED));

        handler.handle(new FeedbackRequestedEvent(sessionId));

        verify(interviewAiService, times(2)).generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE);
        verify(stateService).complete(sessionId, valid);
        verify(stateService, never()).complete(sessionId, invalid);
    }

    @Test
    @DisplayName("계약 위반 결과가 3회 반복되면 FAILED로 전이한다")
    void handle_invalidResultThreeTimes_marksFailed() {
        UUID sessionId = UUID.randomUUID();
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE, 3);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);
        InterviewFeedbackResult valid = feedbackResult(PlanTier.FREE);
        InterviewFeedbackResult invalid = new InterviewFeedbackResult(
                0, valid.coachBrief(), valid.scores(), valid.strengths(), valid.improvements(), null);

        given(stateService.start(sessionId)).willReturn(Optional.of(feedbackContext));
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willReturn(invalid);
        given(stateService.fail(eq(sessionId), anyString())).willReturn(FeedbackStatus.FAILED);

        handler.handle(new FeedbackRequestedEvent(sessionId));

        verify(interviewAiService, times(3)).generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE);
        verify(stateService, never()).complete(any(), any());
        verify(stateService).fail(eq(sessionId), anyString());
        verify(feedbackSseManager).send(sessionId, FeedbackStatus.FAILED, "피드백 생성에 실패했습니다.");
        verify(feedbackSseManager).complete(sessionId);
    }

    @Test
    @DisplayName("retry backoff 중 인터럽트가 발생하면 실패 마킹과 SSE 없이 중단한다")
    void handle_interruptedDuringRetryBackoff_doesNotMarkFailed() {
        UUID sessionId = UUID.randomUUID();
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE, 3);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);

        given(stateService.start(sessionId)).willReturn(Optional.of(feedbackContext));
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willThrow(new RuntimeException("temporary ai error"));

        try {
            Thread.currentThread().interrupt();

            handler.handle(new FeedbackRequestedEvent(sessionId));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(stateService, never()).fail(any(), anyString());
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
        FeedbackGenerationContext generationContext = new FeedbackGenerationContext(
                List.of(), InterviewType.TECHNICAL, PlanTier.FREE, 3);
        FeedbackContext feedbackContext = new FeedbackContext(generationContext, FeedbackStatus.GENERATING);

        given(stateService.start(sessionId)).willReturn(Optional.of(feedbackContext));
        given(interviewAiService.generateFeedback(List.of(), InterviewType.TECHNICAL, PlanTier.FREE))
                .willThrow(new RuntimeException("wrapped", new RuntimeException(new InterruptedException())));

        try {
            handler.handle(new FeedbackRequestedEvent(sessionId));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(stateService, never()).fail(any(), anyString());
            verify(feedbackSseManager, never()).send(eq(sessionId), eq(FeedbackStatus.FAILED), anyString());
            verify(feedbackSseManager, never()).complete(sessionId);
        } finally {
            Thread.interrupted();
        }
    }
}
