package app.mockly.domain.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "subscription_plan",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_product_billing_cycle_currency",
                        columnNames = {"product_id", "billing_cycle", "currency"}
                )
        }
)
public class SubscriptionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private SubscriptionProduct product;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    public boolean isFree() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isLifetime() {
        return billingCycle == BillingCycle.LIFETIME;
    }
}
