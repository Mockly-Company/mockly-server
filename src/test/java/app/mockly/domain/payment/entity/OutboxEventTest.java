package app.mockly.domain.payment.entity;

import app.mockly.domain.payment.dto.outbox.CreateSchedulePayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    @DisplayName("결제 스케줄 payload는 결제수단 ID만 가진다")
    void createSchedulePayloadContainsOnlyPaymentMethodId() {
        assertThat(CreateSchedulePayload.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("paymentMethodId");
    }

    @Test
    @DisplayName("결제 스케줄 생성 이벤트는 전달받은 payload를 저장한다")
    void createScheduleStoresSerializedPayload() {
        String payload = "{\"subscriptionId\":10,\"paymentMethodId\":20}";

        OutboxEvent event = OutboxEvent.createSchedule(10L, payload);

        assertThat(event.getPayload()).isEqualTo(payload);
        assertThat(event.getAggregateId()).isEqualTo(10L);
        assertThat(event.getEventType()).isEqualTo("SCHEDULE_CREATE");
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }
}
