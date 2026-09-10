package app.mockly.domain.payment.scheduler;

import app.mockly.domain.payment.entity.OutboxEvent;
import app.mockly.domain.payment.entity.OutboxEventStatus;
import app.mockly.domain.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxEventTransactionHelper {

    private final OutboxEventRepository outboxEventRepository;
    private final ScheduleCreationHandler scheduleCreationHandler;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(Long eventId) {
        OutboxEvent event = findPendingEvent(eventId);
        if (event == null) {
            return;
        }
        if (!"SCHEDULE_CREATE".equals(event.getEventType())) {
            throw new NonRetryableOutboxException(
                    "UNSUPPORTED_EVENT_TYPE",
                    "지원하지 않는 Outbox 이벤트 타입입니다."
            );
        }

        scheduleCreationHandler.handle(event);
        event.markAsProcessed();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long eventId, String reason) {
        OutboxEvent event = findPendingEvent(eventId);
        if (event != null) {
            event.markAsFailed(reason);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRetryFailure(Long eventId, String reason, int maxRetryCount) {
        OutboxEvent event = findPendingEvent(eventId);
        if (event == null) {
            return;
        }

        event.recordRetryFailure(reason);
        if (event.getRetryCount() >= maxRetryCount) {
            event.markAsFailed(reason);
        }
    }

    private OutboxEvent findPendingEvent(Long eventId) {
        return outboxEventRepository.findByIdForUpdate(eventId)
                .filter(event -> event.getStatus() == OutboxEventStatus.PENDING)
                .orElse(null);
    }
}
