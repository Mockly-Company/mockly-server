package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.FeedbackContext;
import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewFeedback;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewSessionStatus;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.repository.InterviewFeedbackRepository;
import app.mockly.domain.interview.repository.InterviewMessageRepository;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FeedbackTransactionHelperTest {

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private InterviewMessageRepository interviewMessageRepository;

    @Mock
    private InterviewFeedbackRepository interviewFeedbackRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private FeedbackTransactionHelper txHelper;

    @Test
    @DisplayName("PENDING 세션이면 GENERATING 전이 후 AI context를 반환한다")
    void markGeneratingAndLoadContext_pending_returnsContext() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InterviewSession session = InterviewSession.builder()
                .id(sessionId)
                .position("백엔드 개발자")
                .experienceLevel(ExperienceLevel.JUNIOR)
                .interviewType(InterviewType.TECHNICAL)
                .totalQuestions(3)
                .selfIntroduction("자기소개")
                .currentQuestionNumber(3)
                .status(InterviewSessionStatus.FEEDBACK_PENDING)
                .feedbackStatus(FeedbackStatus.GENERATING)
                .build();

        given(interviewSessionRepository.markFeedbackGeneratingIfPending(
                eq(sessionId), eq(FeedbackStatus.PENDING), eq(FeedbackStatus.GENERATING), any(Instant.class)))
                .willReturn(1);
        given(interviewSessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(subscriptionRepository.findByUserIdAndStatus(eq(userId), any())).willReturn(Optional.empty());
        given(interviewMessageRepository.findBySessionIdOrderByIdAsc(sessionId)).willReturn(List.of());

        Optional<FeedbackContext> result = txHelper.markGeneratingAndLoadContext(sessionId, userId);

        assertThat(result).isPresent();
        assertThat(result.get().feedbackStatus()).isEqualTo(FeedbackStatus.GENERATING);
        assertThat(result.get().context().interviewType()).isEqualTo(InterviewType.TECHNICAL);
    }

    @Test
    @DisplayName("PENDING이 아니면 중복 작업으로 보고 context 로드를 생략한다")
    void markGeneratingAndLoadContext_notPending_returnsEmpty() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        given(interviewSessionRepository.markFeedbackGeneratingIfPending(
                eq(sessionId), eq(FeedbackStatus.PENDING), eq(FeedbackStatus.GENERATING), any(Instant.class)))
                .willReturn(0);
        given(interviewSessionRepository.existsById(sessionId)).willReturn(true);

        Optional<FeedbackContext> result = txHelper.markGeneratingAndLoadContext(sessionId, userId);

        assertThat(result).isEmpty();
        verify(interviewSessionRepository, never()).findById(any());
        verify(interviewMessageRepository, never()).findBySessionIdOrderByIdAsc(any());
        verify(subscriptionRepository, never()).findByUserIdAndStatus(any(), any());
    }

    @Test
    @DisplayName("세션이 없으면 기존처럼 RESOURCE_NOT_FOUND 예외를 던진다")
    void markGeneratingAndLoadContext_missingSession_throwsNotFound() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        given(interviewSessionRepository.markFeedbackGeneratingIfPending(
                eq(sessionId), eq(FeedbackStatus.PENDING), eq(FeedbackStatus.GENERATING), any(Instant.class)))
                .willReturn(0);
        given(interviewSessionRepository.existsById(sessionId)).willReturn(false);

        assertThatThrownBy(() -> txHelper.markGeneratingAndLoadContext(sessionId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("statusCode")
                .isEqualTo(ApiStatusCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("GENERATING 세션이면 완료 전이 후 피드백을 저장한다")
    void saveFeedbackAndComplete_generating_savesFeedback() {
        UUID sessionId = UUID.randomUUID();
        InterviewFeedbackResult result = feedbackResult();

        given(interviewSessionRepository.completeFeedbackIfGenerating(
                eq(sessionId),
                eq(FeedbackStatus.GENERATING),
                eq(FeedbackStatus.COMPLETED),
                eq(InterviewSessionStatus.COMPLETED),
                any(Instant.class),
                any(Instant.class)))
                .willReturn(1);
        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(InterviewSession.builder()
                .id(sessionId)
                .build());

        Optional<FeedbackStatus> status = txHelper.saveFeedbackAndComplete(sessionId, result, "[]");

        assertThat(status).contains(FeedbackStatus.COMPLETED);
        verify(interviewFeedbackRepository).saveAndFlush(any(InterviewFeedback.class));
    }

    @Test
    @DisplayName("이미 완료 처리 중인 늦은 worker는 피드백을 저장하지 않는다")
    void saveFeedbackAndComplete_lateWorker_returnsEmpty() {
        UUID sessionId = UUID.randomUUID();

        given(interviewSessionRepository.completeFeedbackIfGenerating(
                eq(sessionId),
                eq(FeedbackStatus.GENERATING),
                eq(FeedbackStatus.COMPLETED),
                eq(InterviewSessionStatus.COMPLETED),
                any(Instant.class),
                any(Instant.class)))
                .willReturn(0);

        Optional<FeedbackStatus> status = txHelper.saveFeedbackAndComplete(sessionId, feedbackResult(), "[]");

        assertThat(status).isEmpty();
        verify(interviewSessionRepository, never()).getReferenceById(any());
        verifyNoInteractions(interviewFeedbackRepository);
    }

    @Test
    @DisplayName("완료 claim 후 피드백 저장 실패는 완료 저장 예외로 전파한다")
    void saveFeedbackAndComplete_saveFailure_throwsCompletionException() {
        UUID sessionId = UUID.randomUUID();

        given(interviewSessionRepository.completeFeedbackIfGenerating(
                eq(sessionId),
                eq(FeedbackStatus.GENERATING),
                eq(FeedbackStatus.COMPLETED),
                eq(InterviewSessionStatus.COMPLETED),
                any(Instant.class),
                any(Instant.class)))
                .willReturn(1);
        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(InterviewSession.builder()
                .id(sessionId)
                .build());
        given(interviewFeedbackRepository.saveAndFlush(any(InterviewFeedback.class))).willThrow(new RuntimeException("db error"));

        assertThatThrownBy(() -> txHelper.saveFeedbackAndComplete(sessionId, feedbackResult(), "[]"))
                .isInstanceOf(FeedbackTransactionHelper.FeedbackCompletionException.class);
    }

    private InterviewFeedbackResult feedbackResult() {
        return new InterviewFeedbackResult(
                80,
                List.of(new InterviewFeedbackResult.ExpertFeedback("기술 면접관", 80, "좋습니다.")),
                "강점",
                "개선점",
                null
        );
    }
}
