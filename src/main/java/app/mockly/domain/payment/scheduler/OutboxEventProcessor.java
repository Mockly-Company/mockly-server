package app.mockly.domain.payment.scheduler;

import app.mockly.domain.payment.entity.OutboxEvent;
import app.mockly.domain.payment.entity.OutboxEventStatus;
import app.mockly.domain.payment.repository.OutboxEventRepository;
import app.mockly.domain.product.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProcessor {
    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final SubscriptionService subscriptionService;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> events = outboxEventRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);

        for (OutboxEvent event : events) {
            try {
                if ("SCHEDULE_CREATE".equals(event.getEventType())) {
                    subscriptionService.processScheduleCreation(event.getAggregateId());
                }
            } catch (Exception e) {
                event.recordRetryFailure(e.getMessage());
                if (event.getRetryCount() >= MAX_RETRY_COUNT) {
                    event.markAsFailed(e.getMessage());
                    log.error("Outbox 이벤트 최종 실패 - eventId: {}, aggregateId: {}",
                            event.getId(), event.getAggregateId(), e);
                } else {
                    log.warn("Outbox 이벤트 재시도 실패 ({}/{}) - eventId: {}, aggregateId: {}",
                            event.getRetryCount(), MAX_RETRY_COUNT, event.getId(), event.getAggregateId(), e);
                }
            }
        }
    }
}
