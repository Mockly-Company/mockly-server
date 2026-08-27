package app.mockly.domain.interview.service;

import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.event.FeedbackRequestedEvent;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.global.config.InterviewFeedbackProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StaleFeedbackRecoveryJobTest {

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("설정된 stale threshold 기준으로 stuck 세션을 조회한다")
    void recoverStaleFeedbacks_usesConfiguredThreshold() {
        InterviewFeedbackProperties properties = new InterviewFeedbackProperties();
        properties.setStaleThresholdMinutes(10);
        StaleFeedbackRecoveryJob job = new StaleFeedbackRecoveryJob(
                interviewSessionRepository, eventPublisher, properties);
        ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
        Instant before = Instant.now();
        given(interviewSessionRepository.findTop100ByFeedbackStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(List.of(FeedbackStatus.PENDING, FeedbackStatus.GENERATING)), thresholdCaptor.capture()))
                .willReturn(List.of());

        job.recoverStaleFeedbacks();

        Instant after = Instant.now();
        assertThat(thresholdCaptor.getValue())
                .isBetween(before.minusSeconds(10 * 60L), after.minusSeconds(10 * 60L));
        verify(interviewSessionRepository).findTop100ByFeedbackStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(List.of(FeedbackStatus.PENDING, FeedbackStatus.GENERATING)), eq(thresholdCaptor.getValue()));
    }

    @Test
    @DisplayName("stale threshold 기본값은 기존과 동일하게 6분이다")
    void interviewFeedbackProperties_defaultThresholdIsSixMinutes() {
        InterviewFeedbackProperties properties = new InterviewFeedbackProperties();

        assertThat(properties.getStaleThresholdMinutes()).isEqualTo(6);
    }

    @Test
    @DisplayName("조회한 task ID가 현재 소유권과 다르면 stale 복구 이벤트를 발행하지 않는다")
    void recoverStaleFeedbacks_ownershipChanged_doesNotPublishEvent() {
        InterviewFeedbackProperties properties = new InterviewFeedbackProperties();
        properties.setStaleThresholdMinutes(10);
        StaleFeedbackRecoveryJob job = new StaleFeedbackRecoveryJob(
                interviewSessionRepository, eventPublisher, properties);
        UUID sessionId = UUID.randomUUID();
        UUID staleTaskId = UUID.randomUUID();
        InterviewSession staleSession = InterviewSession.builder()
                .id(sessionId)
                .feedbackStatus(FeedbackStatus.GENERATING)
                .feedbackGenerationTaskId(staleTaskId)
                .build();

        given(interviewSessionRepository.findTop100ByFeedbackStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(List.of(FeedbackStatus.PENDING, FeedbackStatus.GENERATING)), any(Instant.class)))
                .willReturn(List.of(staleSession));
        given(interviewSessionRepository.requeueStaleGenerating(
                eq(sessionId),
                eq(staleTaskId),
                eq(FeedbackStatus.GENERATING),
                eq(FeedbackStatus.PENDING),
                any(Instant.class),
                any(Instant.class)))
                .willReturn(0);

        job.recoverStaleFeedbacks();

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("현재 task ID를 가진 stale GENERATING 작업만 PENDING으로 되돌리고 이벤트를 발행한다")
    void recoverStaleFeedbacks_ownedGenerating_requeuesAndPublishesEvent() {
        InterviewFeedbackProperties properties = new InterviewFeedbackProperties();
        properties.setStaleThresholdMinutes(10);
        StaleFeedbackRecoveryJob job = new StaleFeedbackRecoveryJob(
                interviewSessionRepository, eventPublisher, properties);
        UUID sessionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        InterviewSession staleSession = InterviewSession.builder()
                .id(sessionId)
                .feedbackStatus(FeedbackStatus.GENERATING)
                .feedbackGenerationTaskId(taskId)
                .build();

        given(interviewSessionRepository.findTop100ByFeedbackStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(List.of(FeedbackStatus.PENDING, FeedbackStatus.GENERATING)), any(Instant.class)))
                .willReturn(List.of(staleSession));
        given(interviewSessionRepository.requeueStaleGenerating(
                eq(sessionId),
                eq(taskId),
                eq(FeedbackStatus.GENERATING),
                eq(FeedbackStatus.PENDING),
                any(Instant.class),
                any(Instant.class)))
                .willReturn(1);

        job.recoverStaleFeedbacks();

        verify(eventPublisher).publishEvent(new FeedbackRequestedEvent(sessionId));
    }

    @Test
    @DisplayName("다른 Job이 먼저 갱신한 stale PENDING 작업은 이벤트를 중복 발행하지 않는다")
    void recoverStaleFeedbacks_pendingAlreadyRequeued_doesNotPublishEvent() {
        InterviewFeedbackProperties properties = new InterviewFeedbackProperties();
        properties.setStaleThresholdMinutes(10);
        StaleFeedbackRecoveryJob job = new StaleFeedbackRecoveryJob(
                interviewSessionRepository, eventPublisher, properties);
        UUID sessionId = UUID.randomUUID();
        InterviewSession staleSession = InterviewSession.builder()
                .id(sessionId)
                .feedbackStatus(FeedbackStatus.PENDING)
                .build();

        given(interviewSessionRepository.findTop100ByFeedbackStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(List.of(FeedbackStatus.PENDING, FeedbackStatus.GENERATING)), any(Instant.class)))
                .willReturn(List.of(staleSession));
        given(interviewSessionRepository.requeueStalePending(
                eq(sessionId),
                eq(FeedbackStatus.PENDING),
                any(Instant.class),
                any(Instant.class)))
                .willReturn(0);

        job.recoverStaleFeedbacks();

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("stale PENDING 작업 복구에 성공하면 피드백 생성 이벤트를 발행한다")
    void recoverStaleFeedbacks_stalePending_requeuesAndPublishesEvent() {
        InterviewFeedbackProperties properties = new InterviewFeedbackProperties();
        properties.setStaleThresholdMinutes(10);
        StaleFeedbackRecoveryJob job = new StaleFeedbackRecoveryJob(
                interviewSessionRepository, eventPublisher, properties);
        UUID sessionId = UUID.randomUUID();
        InterviewSession staleSession = InterviewSession.builder()
                .id(sessionId)
                .feedbackStatus(FeedbackStatus.PENDING)
                .build();

        given(interviewSessionRepository.findTop100ByFeedbackStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(List.of(FeedbackStatus.PENDING, FeedbackStatus.GENERATING)), any(Instant.class)))
                .willReturn(List.of(staleSession));
        given(interviewSessionRepository.requeueStalePending(
                eq(sessionId),
                eq(FeedbackStatus.PENDING),
                any(Instant.class),
                any(Instant.class)))
                .willReturn(1);

        job.recoverStaleFeedbacks();

        verify(eventPublisher).publishEvent(new FeedbackRequestedEvent(sessionId));
    }

    @Test
    @DisplayName("여러 stale 작업 중 조건부 복구에 성공한 세션의 이벤트만 발행한다")
    void recoverStaleFeedbacks_mixedResults_publishesOnlyRecoveredSessionEvent() {
        InterviewFeedbackProperties properties = new InterviewFeedbackProperties();
        properties.setStaleThresholdMinutes(10);
        StaleFeedbackRecoveryJob job = new StaleFeedbackRecoveryJob(
                interviewSessionRepository, eventPublisher, properties);
        UUID recoveredSessionId = UUID.randomUUID();
        UUID skippedSessionId = UUID.randomUUID();
        UUID recoveredTaskId = UUID.randomUUID();
        InterviewSession recoveredSession = InterviewSession.builder()
                .id(recoveredSessionId)
                .feedbackStatus(FeedbackStatus.GENERATING)
                .feedbackGenerationTaskId(recoveredTaskId)
                .build();
        InterviewSession skippedSession = InterviewSession.builder()
                .id(skippedSessionId)
                .feedbackStatus(FeedbackStatus.PENDING)
                .build();

        given(interviewSessionRepository.findTop100ByFeedbackStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(List.of(FeedbackStatus.PENDING, FeedbackStatus.GENERATING)), any(Instant.class)))
                .willReturn(List.of(recoveredSession, skippedSession));
        given(interviewSessionRepository.requeueStaleGenerating(
                eq(recoveredSessionId),
                eq(recoveredTaskId),
                eq(FeedbackStatus.GENERATING),
                eq(FeedbackStatus.PENDING),
                any(Instant.class),
                any(Instant.class)))
                .willReturn(1);
        given(interviewSessionRepository.requeueStalePending(
                eq(skippedSessionId),
                eq(FeedbackStatus.PENDING),
                any(Instant.class),
                any(Instant.class)))
                .willReturn(0);

        job.recoverStaleFeedbacks();

        verify(eventPublisher).publishEvent(new FeedbackRequestedEvent(recoveredSessionId));
        verify(eventPublisher, never()).publishEvent(new FeedbackRequestedEvent(skippedSessionId));
    }

    @Test
    @DisplayName("task ID가 없는 GENERATING 세션은 복구하지 않고 데이터 이상 경고를 남긴다")
    void recoverStaleFeedbacks_generatingWithoutTaskId_warnsAndSkips(CapturedOutput output) {
        InterviewFeedbackProperties properties = new InterviewFeedbackProperties();
        properties.setStaleThresholdMinutes(10);
        StaleFeedbackRecoveryJob job = new StaleFeedbackRecoveryJob(
                interviewSessionRepository, eventPublisher, properties);
        UUID sessionId = UUID.randomUUID();
        InterviewSession invalidSession = InterviewSession.builder()
                .id(sessionId)
                .feedbackStatus(FeedbackStatus.GENERATING)
                .feedbackGenerationTaskId(null)
                .build();

        given(interviewSessionRepository.findTop100ByFeedbackStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(List.of(FeedbackStatus.PENDING, FeedbackStatus.GENERATING)), any(Instant.class)))
                .willReturn(List.of(invalidSession));

        job.recoverStaleFeedbacks();

        verify(interviewSessionRepository, never()).requeueStaleGenerating(
                any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
        assertThat(output).contains("task ID가 없는 GENERATING 세션 복구 생략");
    }
}
