package app.mockly.domain.payment.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventWorkerTest {

    @Test
    @DisplayName("Outbox worker는 트랜잭션 helper를 통해 이벤트를 처리한다")
    void workerDependsOnTransactionHelper() {
        assertThat(OutboxEventWorker.class.getDeclaredFields())
                .extracting(field -> field.getType().getSimpleName())
                .contains("OutboxEventTransactionHelper");
    }

    @Test
    @DisplayName("영구 오류는 재시도하지 않고 별도 트랜잭션으로 실패 기록한다")
    void permanentFailureIsMarkedAsFailed() {
        OutboxEventTransactionHelper transactionHelper = mock(OutboxEventTransactionHelper.class);
        OutboxEventWorker worker = new OutboxEventWorker(transactionHelper);
        doThrow(new NonRetryableOutboxException("INVALID_PAYLOAD", "잘못된 payload"))
                .when(transactionHelper).processEvent(1L);

        worker.process(1L);

        verify(transactionHelper).markAsFailed(1L, "INVALID_PAYLOAD");
    }

    @Test
    @DisplayName("일시 오류는 별도 트랜잭션으로 재시도 실패를 기록한다")
    void transientFailureIsRecordedForRetry() {
        OutboxEventTransactionHelper transactionHelper = mock(OutboxEventTransactionHelper.class);
        OutboxEventWorker worker = new OutboxEventWorker(transactionHelper);
        doThrow(new RuntimeException("temporary failure"))
                .when(transactionHelper).processEvent(1L);

        worker.process(1L);

        verify(transactionHelper).recordRetryFailure(1L, "temporary failure", 5);
    }
}
