package app.mockly.domain.payment.scheduler;

import app.mockly.domain.payment.entity.OutboxEvent;
import app.mockly.domain.payment.entity.OutboxEventStatus;
import app.mockly.domain.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

@SpringBootTest
class OutboxEventTransactionIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private OutboxEventTransactionHelper transactionHelper;
    @MockitoBean
    private ScheduleCreationHandler scheduleCreationHandler;

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAll();
    }

    @Test
    @DisplayName("처리 트랜잭션이 롤백돼도 재시도 상태는 별도 트랜잭션으로 저장한다")
    void retryStatePersistsAfterProcessingTransactionRollback() {
        OutboxEvent event = outboxEventRepository.saveAndFlush(
                OutboxEvent.createSchedule(10L, "{\"paymentMethodId\":20}")
        );
        willThrow(new RuntimeException("temporary failure"))
                .given(scheduleCreationHandler).handle(any(OutboxEvent.class));

        assertThatThrownBy(() -> transactionHelper.processEvent(event.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("temporary failure");

        OutboxEvent rolledBack = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(rolledBack.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(rolledBack.getRetryCount()).isZero();

        transactionHelper.recordRetryFailure(event.getId(), "temporary failure", 5);

        OutboxEvent retried = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(retried.getRetryCount()).isEqualTo(1);
    }
}
