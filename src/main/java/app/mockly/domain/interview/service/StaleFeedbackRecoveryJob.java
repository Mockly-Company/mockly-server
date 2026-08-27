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
                .findTop100ByFeedbackStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        List.of(FeedbackStatus.PENDING, FeedbackStatus.GENERATING), threshold);

        int recoveredCount = 0;
        for (InterviewSession session : staleSessions) {
            if (recover(session, threshold)) {
                log.warn("stuck 피드백 복구: sessionId={}, feedbackStatus={}, updatedAt={}",
                        session.getId(), session.getFeedbackStatus(), session.getUpdatedAt());
                eventPublisher.publishEvent(new FeedbackRequestedEvent(session.getId()));
                recoveredCount++;
            }
        }

        if (recoveredCount > 0) {
            log.info("stuck 피드백 {}건 복구 완료", recoveredCount);
        }
    }

    private boolean recover(InterviewSession session, Instant threshold) {
        if (session.getFeedbackStatus() == FeedbackStatus.GENERATING) {
            return recoverGenerating(session, threshold);
        }
        return recoverPending(session, threshold);
    }

    private boolean recoverGenerating(InterviewSession session, Instant threshold) {
        if (session.getFeedbackGenerationTaskId() == null) {
            log.warn("task ID가 없는 GENERATING 세션 복구 생략: sessionId={}, updatedAt={}",
                    session.getId(), session.getUpdatedAt());
            return false;
        }
        return interviewSessionRepository.requeueStaleGenerating(
                session.getId(), session.getFeedbackGenerationTaskId(),
                FeedbackStatus.GENERATING, FeedbackStatus.PENDING,
                threshold,
                Instant.now()) == 1;
    }

    private boolean recoverPending(InterviewSession session, Instant threshold) {
        return interviewSessionRepository.requeueStalePending(
                session.getId(),
                FeedbackStatus.PENDING,
                threshold,
                Instant.now()) == 1;
    }
}
