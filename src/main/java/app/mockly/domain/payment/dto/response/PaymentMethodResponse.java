package app.mockly.domain.payment.dto.response;

import app.mockly.domain.payment.entity.PaymentMethod;

import java.time.Instant;

public record PaymentMethodResponse(
        Long id,
        String cardBrand,
        String cardNumber,
        Boolean isDefault,
        Instant createdAt
) {
    public static PaymentMethodResponse from(PaymentMethod paymentMethod) {
        return new PaymentMethodResponse(
                paymentMethod.getId(),
                paymentMethod.getCardBrand(),
                paymentMethod.getCardNumber(),
                paymentMethod.isDefault(),
                paymentMethod.getCreatedAt()
        );
    }
}
