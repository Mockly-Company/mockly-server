package app.mockly.domain.payment.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventWorker {

    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventTransactionHelper transactionHelper;

    public void process(Long eventId) {
        try {
            transactionHelper.processEvent(eventId);
        } catch (NonRetryableOutboxException e) {
            transactionHelper.markAsFailed(eventId, e.getFailureCode());
            log.error("Outbox 이벤트 영구 실패 - eventId: {}, failureCode: {}",
                    eventId, e.getFailureCode(), e);
        } catch (Exception e) {
            transactionHelper.recordRetryFailure(eventId, e.getMessage(), MAX_RETRY_COUNT);
            log.warn("Outbox 이벤트 처리 실패 - eventId: {}", eventId, e);
        }
    }
}
