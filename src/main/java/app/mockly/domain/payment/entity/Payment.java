package app.mockly.domain.payment.entity;

import app.mockly.domain.product.entity.Currency;
import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private String paymentId;

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

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failed_reason")
    private String failedReason;

    public static Payment create(Invoice invoice, String paymentId,
                                  BigDecimal amount, Currency currency) {
        return Payment.builder()
                .invoice(invoice)
                .paymentId(paymentId)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.PENDING)
                .build();
    }

    public void markAsPaid(String paymentMethod) {
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
