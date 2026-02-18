package app.mockly.domain.payment.dto.response;

import app.mockly.domain.payment.entity.Payment;
import app.mockly.domain.payment.entity.PaymentMethodType;
import app.mockly.domain.payment.entity.PaymentStatus;
import app.mockly.domain.product.entity.BillingCycle;
import app.mockly.domain.product.entity.SubscriptionPlan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GetPaymentDetailResponse(
        String id,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        PaymentMethodType paymentMethod,
        LocalDateTime paidAt,
        ProductInfo product
) {
    public static GetPaymentDetailResponse from(Payment payment) {
        SubscriptionPlan plan = payment.getInvoice().getSubscription().getSubscriptionPlan();
        return new GetPaymentDetailResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getCurrency().name(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                payment.getPaidAt(),
                new ProductInfo(
                        plan.getProduct().getName(),
                        plan.getPrice(),
                        plan.getBillingCycle()
                )
        );
    }

    public record ProductInfo(
            String name,
            BigDecimal price,
            BillingCycle billingCycle
    ) {
    }
}
