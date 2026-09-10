package app.mockly.domain.payment.scheduler;

import app.mockly.domain.payment.entity.OutboxEvent;
import app.mockly.domain.payment.entity.OutboxEventStatus;
import app.mockly.domain.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventTransactionHelperBehaviorTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ScheduleCreationHandler scheduleCreationHandler;
    @InjectMocks
    private OutboxEventTransactionHelper transactionHelper;

    @Test
    @DisplayName("이벤트 ID로 조회한 한 건을 처리하고 성공 상태로 변경한다")
    void processEventMarksEventAsProcessed() {
        OutboxEvent event = pendingEvent();
        given(outboxEventRepository.findByIdForUpdate(1L)).willReturn(Optional.of(event));

        transactionHelper.processEvent(1L);

        verify(scheduleCreationHandler).handle(event);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
    }

    @Test
    @DisplayName("영구 오류는 별도 트랜잭션에서 실패 상태로 기록한다")
    void markAsFailedUpdatesPendingEvent() {
        OutboxEvent event = pendingEvent();
        given(outboxEventRepository.findByIdForUpdate(1L)).willReturn(Optional.of(event));

        transactionHelper.markAsFailed(1L, "INVALID_PAYLOAD");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getFailReason()).isEqualTo("INVALID_PAYLOAD");
    }

    @Test
    @DisplayName("일시 오류는 재시도 횟수를 증가시키고 PENDING 상태를 유지한다")
    void recordRetryFailureKeepsEventPendingBeforeLimit() {
        OutboxEvent event = pendingEvent();
        given(outboxEventRepository.findByIdForUpdate(1L)).willReturn(Optional.of(event));

        transactionHelper.recordRetryFailure(1L, "timeout", 5);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("최대 재시도 횟수에 도달하면 최종 실패 상태로 변경한다")
    void recordRetryFailureMarksEventFailedAtLimit() {
        OutboxEvent event = pendingEvent();
        for (int attempt = 0; attempt < 4; attempt++) {
            event.recordRetryFailure("timeout");
        }
        given(outboxEventRepository.findByIdForUpdate(1L)).willReturn(Optional.of(event));

        transactionHelper.recordRetryFailure(1L, "timeout", 5);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(5);
    }

    private OutboxEvent pendingEvent() {
        return OutboxEvent.createSchedule(10L, "{\"paymentMethodId\":20}");
    }
}
