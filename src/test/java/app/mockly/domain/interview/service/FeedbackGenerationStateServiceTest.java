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
import app.mockly.domain.product.entity.PlanTier;
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
import static app.mockly.domain.interview.FeedbackTestFixtures.feedbackResult;

@ExtendWith(MockitoExtension.class)
class FeedbackGenerationStateServiceTest {

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private InterviewMessageRepository interviewMessageRepository;

    @Mock
    private InterviewFeedbackRepository interviewFeedbackRepository;

    @InjectMocks
    private FeedbackGenerationStateService stateService;

    @Test
    @DisplayName("PENDING 세션이면 GENERATING 전이 후 AI context를 반환한다")
    void start_pending_returnsContext() {
        UUID sessionId = UUID.randomUUID();
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
                .feedbackGenerationTier(PlanTier.PRO)
                .build();

        given(interviewSessionRepository.markFeedbackGeneratingIfPending(
                eq(sessionId), eq(FeedbackStatus.PENDING), eq(FeedbackStatus.GENERATING),
                any(UUID.class), any(Instant.class)))
                .willReturn(1);
        given(interviewSessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(interviewMessageRepository.findBySessionIdOrderByIdAsc(sessionId)).willReturn(List.of());

        Optional<FeedbackContext> result = stateService.start(sessionId);

        assertThat(result).isPresent();
        assertThat(result.get().taskId()).isNotNull();
        assertThat(result.get().feedbackStatus()).isEqualTo(FeedbackStatus.GENERATING);
        assertThat(result.get().context().interviewType()).isEqualTo(InterviewType.TECHNICAL);
        assertThat(result.get().context().planTier()).isEqualTo(PlanTier.PRO);
    }

    @Test
    @DisplayName("PENDING이 아니면 중복 작업으로 보고 context 로드를 생략한다")
    void start_notPending_returnsEmpty() {
        UUID sessionId = UUID.randomUUID();

        given(interviewSessionRepository.markFeedbackGeneratingIfPending(
                eq(sessionId), eq(FeedbackStatus.PENDING), eq(FeedbackStatus.GENERATING),
                any(UUID.class), any(Instant.class)))
                .willReturn(0);
        given(interviewSessionRepository.existsById(sessionId)).willReturn(true);

        Optional<FeedbackContext> result = stateService.start(sessionId);

        assertThat(result).isEmpty();
        verify(interviewSessionRepository, never()).findById(any());
        verify(interviewMessageRepository, never()).findBySessionIdOrderByIdAsc(any());
    }

    @Test
    @DisplayName("세션이 없으면 기존처럼 RESOURCE_NOT_FOUND 예외를 던진다")
    void start_missingSession_throwsNotFound() {
        UUID sessionId = UUID.randomUUID();

        given(interviewSessionRepository.markFeedbackGeneratingIfPending(
                eq(sessionId), eq(FeedbackStatus.PENDING), eq(FeedbackStatus.GENERATING),
                any(UUID.class), any(Instant.class)))
                .willReturn(0);
        given(interviewSessionRepository.existsById(sessionId)).willReturn(false);

        assertThatThrownBy(() -> stateService.start(sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting("statusCode")
                .isEqualTo(ApiStatusCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("GENERATING 세션이면 완료 전이 후 피드백을 저장한다")
    void complete_generating_savesFeedback() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        InterviewFeedbackResult result = feedbackResult(PlanTier.PRO);

        given(interviewSessionRepository.completeFeedbackIfOwned(
                eq(sessionId),
                eq(taskId),
                eq(FeedbackStatus.GENERATING),
                eq(FeedbackStatus.COMPLETED),
                eq(InterviewSessionStatus.COMPLETED),
                any(Instant.class),
                any(Instant.class)))
                .willReturn(1);
        given(interviewSessionRepository.findById(sessionId)).willReturn(Optional.of(InterviewSession.builder()
                .id(sessionId)
                .feedbackGenerationTier(PlanTier.PRO)
                .build()));

        Optional<FeedbackStatus> status = stateService.complete(sessionId, taskId, result);

        assertThat(status).contains(FeedbackStatus.COMPLETED);
        verify(interviewFeedbackRepository).saveAndFlush(any(InterviewFeedback.class));
    }

    @Test
    @DisplayName("이미 완료 처리 중인 늦은 worker는 피드백을 저장하지 않는다")
    void complete_lateWorker_returnsEmpty() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        given(interviewSessionRepository.completeFeedbackIfOwned(
                eq(sessionId),
                eq(taskId),
                eq(FeedbackStatus.GENERATING),
                eq(FeedbackStatus.COMPLETED),
                eq(InterviewSessionStatus.COMPLETED),
                any(Instant.class),
                any(Instant.class)))
                .willReturn(0);

        Optional<FeedbackStatus> status = stateService.complete(
                sessionId, taskId, feedbackResult(PlanTier.PRO));

        assertThat(status).isEmpty();
        verify(interviewSessionRepository, never()).findById(any());
        verifyNoInteractions(interviewFeedbackRepository);
    }

    @Test
    @DisplayName("현재 작업과 다른 task ID를 가진 늦은 worker는 피드백을 저장하지 않는다")
    void complete_differentTaskId_returnsEmpty() {
        UUID sessionId = UUID.randomUUID();
        UUID staleTaskId = UUID.randomUUID();

        given(interviewSessionRepository.completeFeedbackIfOwned(
                eq(sessionId),
                eq(staleTaskId),
                eq(FeedbackStatus.GENERATING),
                eq(FeedbackStatus.COMPLETED),
                eq(InterviewSessionStatus.COMPLETED),
                any(Instant.class),
                any(Instant.class)))
                .willReturn(0);

        Optional<FeedbackStatus> status = stateService.complete(
                sessionId, staleTaskId, feedbackResult(PlanTier.PRO));

        assertThat(status).isEmpty();
        verify(interviewSessionRepository, never()).findById(any());
        verifyNoInteractions(interviewFeedbackRepository);
    }

    @Test
    @DisplayName("완료 claim 후 피드백 저장 실패는 완료 저장 예외로 전파한다")
    void complete_saveFailure_throwsCompletionException() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        given(interviewSessionRepository.completeFeedbackIfOwned(
                eq(sessionId),
                eq(taskId),
                eq(FeedbackStatus.GENERATING),
                eq(FeedbackStatus.COMPLETED),
                eq(InterviewSessionStatus.COMPLETED),
                any(Instant.class),
                any(Instant.class)))
                .willReturn(1);
        given(interviewSessionRepository.findById(sessionId)).willReturn(Optional.of(InterviewSession.builder()
                .id(sessionId)
                .feedbackGenerationTier(PlanTier.PRO)
                .build()));
        given(interviewFeedbackRepository.saveAndFlush(any(InterviewFeedback.class))).willThrow(new RuntimeException("db error"));

        assertThatThrownBy(() -> stateService.complete(sessionId, taskId, feedbackResult(PlanTier.PRO)))
                .isInstanceOf(FeedbackGenerationStateService.FeedbackCompletionException.class);
    }

    @Test
    @DisplayName("현재 task ID의 worker만 피드백을 실패로 전이한다")
    void fail_ownedTask_marksFailed() {
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        given(interviewSessionRepository.failFeedbackIfOwned(
                eq(sessionId),
                eq(taskId),
                eq(FeedbackStatus.GENERATING),
                eq(FeedbackStatus.FAILED),
                eq("ai error"),
                any(Instant.class)))
                .willReturn(1);

        Optional<FeedbackStatus> status = stateService.fail(sessionId, taskId, "ai error");

        assertThat(status).contains(FeedbackStatus.FAILED);
    }

    @Test
    @DisplayName("현재 작업과 다른 task ID를 가진 늦은 worker는 실패 상태를 덮어쓰지 않는다")
    void fail_differentTaskId_returnsEmpty() {
        UUID sessionId = UUID.randomUUID();
        UUID staleTaskId = UUID.randomUUID();

        given(interviewSessionRepository.failFeedbackIfOwned(
                eq(sessionId),
                eq(staleTaskId),
                eq(FeedbackStatus.GENERATING),
                eq(FeedbackStatus.FAILED),
                eq("late error"),
                any(Instant.class)))
                .willReturn(0);

        Optional<FeedbackStatus> status = stateService.fail(sessionId, staleTaskId, "late error");

        assertThat(status).isEmpty();
    }
}
