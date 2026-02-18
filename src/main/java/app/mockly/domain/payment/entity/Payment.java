package app.mockly.domain.payment.entity;

import app.mockly.domain.product.entity.Currency;
import app.mockly.global.common.BaseEntity;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment extends BaseEntity {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethodType paymentMethod;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failed_reason")
    private String failedReason;

    public static Payment create(Invoice invoice, BigDecimal amount, Currency currency) {
        return Payment.builder()
                .id(generatePaymentId())  // PortOne API용 ID
                .invoice(invoice)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.PENDING)
                .build();
    }

    private static String generatePaymentId() {
        UUID uuid = Generators.timeBasedEpochGenerator().generate();
        return "pay_" + uuid.toString();
    }

    public void markAsPaid(PaymentMethodType paymentMethod) {
        this.status = PaymentStatus.PAID;
        this.paymentMethod = paymentMethod;
        this.paidAt = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failedReason = reason;
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELED;
    }
}
