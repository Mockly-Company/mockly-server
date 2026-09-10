package app.mockly.domain.payment.dto.outbox;

public record CreateSchedulePayload(
        Long paymentMethodId
) {
}
