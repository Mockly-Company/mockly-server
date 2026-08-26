package app.mockly.domain.interview.service;

import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.event.FeedbackRequestedEvent;
import app.mockly.domain.interview.repository.InterviewSessionRepository;
import app.mockly.global.config.InterviewFeedbackProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StaleFeedbackRecoveryJob {

    private final InterviewSessionRepository interviewSessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final InterviewFeedbackProperties interviewFeedbackProperties;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void recoverStaleFeedbacks() {
        Instant threshold = Instant.now().minusSeconds(interviewFeedbackProperties.getStaleThresholdMinutes() * 60L);
        List<InterviewSession> staleSessions = interviewSessionRepository
                .findByFeedbackStatusInAndUpdatedAtBefore(
                        List.of(FeedbackStatus.PENDING, FeedbackStatus.GENERATING), threshold);

        for (InterviewSession session : staleSessions) {
            log.warn("stuck 피드백 복구: sessionId={}, feedbackStatus={}, updatedAt={}",
                    session.getId(), session.getFeedbackStatus(), session.getUpdatedAt());
            session.resetFeedbackStatus();
            eventPublisher.publishEvent(new FeedbackRequestedEvent(session.getId()));
        }

        if (!staleSessions.isEmpty()) {
            log.info("stuck 피드백 {}건 복구 완료", staleSessions.size());
        }
    }
}
