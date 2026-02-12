package app.mockly.domain.payment.dto.response;

import app.mockly.domain.payment.entity.Payment;
import app.mockly.domain.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GetPaymentsResponse(
        List<PaymentSummary> payments,
        PaginationInfo pagination
) {
    public static GetPaymentsResponse from(Page<Payment> page) {
        List<PaymentSummary> payments = page.getContent().stream()
                .map(PaymentSummary::from)
                .toList();
        return new GetPaymentsResponse(payments, PaginationInfo.from(page));
    }

    public record PaymentSummary(
            String id,
            String name,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            LocalDateTime paidAt
    ) {
        public static PaymentSummary from(Payment payment) {
            return new PaymentSummary(
                    payment.getId(),
                    payment.getInvoice().getSubscription()
                            .getSubscriptionPlan().getProduct().getName(),
                    payment.getAmount(),
                    payment.getCurrency().name(),
                    payment.getStatus(),
                    payment.getPaidAt()
            );
        }
    }
}
