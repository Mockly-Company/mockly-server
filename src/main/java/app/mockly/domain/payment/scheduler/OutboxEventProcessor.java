package app.mockly.domain.payment.scheduler;

import app.mockly.domain.payment.entity.OutboxEventStatus;
import app.mockly.domain.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventWorker outboxEventWorker;

    @Scheduled(fixedDelay = 60000)
    public void processOutboxEvents() {
        List<Long> eventIds = outboxEventRepository.findIdsByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING,
                PageRequest.of(0, 20)
        );

        for (Long eventId : eventIds) {
            try {
                outboxEventWorker.process(eventId);
            } catch (Exception e) {
                log.error("Outbox worker 실행 실패 - eventId: {}", eventId, e);
            }
        }
    }
}
