package app.mockly.domain.product.entity;

import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "subscription")
public class Subscription extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan subscriptionPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "cancelled_at")
    private LocalDateTime canceledAt;

    @Setter
    @Column(name = "current_payment_schedule_id")
    private String currentPaymentScheduleId;

    public static Subscription create(UUID userId, SubscriptionPlan subscriptionPlan) {
        return Subscription.builder()
                .userId(userId)
                .subscriptionPlan(subscriptionPlan)
                .status(SubscriptionStatus.PENDING)
                .build();
    }

    public void activate() {
        this.status = SubscriptionStatus.ACTIVE;
        LocalDateTime now = LocalDateTime.now();
        this.startedAt = now;
        this.currentPeriodStart = now;
        this.currentPeriodEnd = calculatePeriodEnd(now);
    }

    private LocalDateTime calculatePeriodEnd(LocalDateTime start) {
        return switch (subscriptionPlan.getBillingCycle()) {
            case MONTHLY -> start.plusMonths(1);
            case YEARLY -> start.plusYears(1);
            case LIFETIME -> null;
        };
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }

    public boolean isCanceled() {
        return status == SubscriptionStatus.CANCELED;
    }

    public void extendPeriod() {
        LocalDateTime now = LocalDateTime.now();
        this.currentPeriodStart = now;
        this.currentPeriodEnd = calculatePeriodEnd(now);
    }
}
