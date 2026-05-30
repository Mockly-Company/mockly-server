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
}
