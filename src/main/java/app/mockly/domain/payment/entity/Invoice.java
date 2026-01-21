package app.mockly.domain.payment.entity;

import app.mockly.domain.product.entity.Currency;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoice")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Invoice extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Builder.Default
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    private List<Payment> payments = new ArrayList<>();

    public static Invoice create(Subscription subscription, BigDecimal amount,
                                  Currency currency, LocalDateTime periodStart,
                                  LocalDateTime periodEnd) {
        return Invoice.builder()
                .subscription(subscription)
                .amount(amount)
                .currency(currency)
                .status(InvoiceStatus.PENDING)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .build();
    }

    public void markAsPaid() {
        this.status = InvoiceStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    public void markAsFailed() {
        this.status = InvoiceStatus.FAILED;
    }
}
