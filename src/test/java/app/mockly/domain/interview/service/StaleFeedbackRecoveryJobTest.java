package app.mockly.domain.interview.service;

import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.global.config.InterviewFeedbackProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
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
        given(interviewSessionRepository.findByFeedbackStatusInAndUpdatedAtBefore(
                eq(List.of(FeedbackStatus.PENDING, FeedbackStatus.GENERATING)), thresholdCaptor.capture()))
                .willReturn(List.of());

        job.recoverStaleFeedbacks();

        Instant after = Instant.now();
        assertThat(thresholdCaptor.getValue())
                .isBetween(before.minusSeconds(10 * 60L), after.minusSeconds(10 * 60L));
        verify(interviewSessionRepository).findByFeedbackStatusInAndUpdatedAtBefore(
                eq(List.of(FeedbackStatus.PENDING, FeedbackStatus.GENERATING)), eq(thresholdCaptor.getValue()));
    }

    @Test
    @DisplayName("stale threshold 기본값은 기존과 동일하게 6분이다")
    void interviewFeedbackProperties_defaultThresholdIsSixMinutes() {
        InterviewFeedbackProperties properties = new InterviewFeedbackProperties();

        assertThat(properties.getStaleThresholdMinutes()).isEqualTo(6);
    }
}
