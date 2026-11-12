package app.mockly.domain.product.entity;

import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "subscription")
public class Subscription extends BaseEntity {
    private static final int GRACE_PERIOD_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan subscriptionPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheduled_plan_id")
    private SubscriptionPlan scheduledPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "cancelled_at")
    private LocalDateTime canceledAt;

    @Column(name = "past_due_at")
    private Instant pastDueAt;

    @Builder.Default
    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd = false;

    @Column(name = "current_marker")
    private Boolean currentMarker;

    @Setter
    @Column(name = "current_payment_schedule_id")
    private String currentPaymentScheduleId;

    public static Subscription create(UUID userId, SubscriptionPlan subscriptionPlan) {
        return create(userId, subscriptionPlan, null);
    }

    public static Subscription create(UUID userId, SubscriptionPlan subscriptionPlan, PaymentMethod paymentMethod) {
        return Subscription.builder()
                .userId(userId)
                .subscriptionPlan(subscriptionPlan)
                .paymentMethod(paymentMethod)
                .status(SubscriptionStatus.PENDING)
                .cancelAtPeriodEnd(false)
                .currentMarker(true)
                .build();
    }

    public void activate() {
        this.status = SubscriptionStatus.ACTIVE;
        this.pastDueAt = null;
        this.cancelAtPeriodEnd = false;
        this.currentMarker = true;
        LocalDateTime now = LocalDateTime.now();
        if (this.activatedAt == null) {
            this.activatedAt = Instant.now();
        }
        this.startedAt = now;
        this.currentPeriodStart = now;
        this.currentPeriodEnd = calculatePeriodEnd(now);
    }

    private LocalDateTime calculatePeriodEnd(LocalDateTime start) {
        return calculatePeriodEnd(start, subscriptionPlan.getBillingCycle());
    }

    private LocalDateTime calculatePeriodEnd(LocalDateTime start, BillingCycle billingCycle) {
        return switch (billingCycle) {
            case MONTHLY -> start.plusMonths(1);
            case YEARLY -> start.plusYears(1);
            case LIFETIME -> null;
        };
    }

    public void scheduleCancelAtPeriodEnd() {
        scheduleCancelAtPeriodEnd(LocalDateTime.now());
    }

    public void scheduleCancelAtPeriodEnd(LocalDateTime occurredAt) {
        this.cancelAtPeriodEnd = true;
        this.canceledAt = occurredAt;
        this.scheduledPlan = null;
        this.currentPaymentScheduleId = null;
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.cancelAtPeriodEnd = false;
        this.scheduledPlan = null;
        this.currentPaymentScheduleId = null;
        this.currentMarker = null;
    }

    public void changePaymentMethod(PaymentMethod newPaymentMethod) {
        this.paymentMethod = newPaymentMethod;
    }

    public void recoverFromDelinquency(PaymentMethod newPaymentMethod, LocalDateTime now) {
        applyScheduledPlanIfPresent();
        this.paymentMethod = newPaymentMethod;
        this.status = SubscriptionStatus.ACTIVE;
        this.pastDueAt = null;
        this.cancelAtPeriodEnd = false;
        this.currentMarker = true;
        if (this.activatedAt == null) {
            this.activatedAt = Instant.now();
        }
        this.currentPeriodStart = now;
        this.currentPeriodEnd = calculatePeriodEnd(now);
    }

    public void upgradePlanImmediately(SubscriptionPlan targetPlan, PaymentMethod paymentMethod) {
        this.subscriptionPlan = targetPlan;
        this.scheduledPlan = null;
        this.cancelAtPeriodEnd = false;
        this.canceledAt = null;
        if (paymentMethod != null) {
            this.paymentMethod = paymentMethod;
        }
    }

    public void scheduleDowngrade(SubscriptionPlan targetPlan) {
        this.scheduledPlan = targetPlan;
        this.cancelAtPeriodEnd = false;
        this.canceledAt = null;
    }

    public void applyScheduledPlanIfPresent() {
        if (this.scheduledPlan != null) {
            this.subscriptionPlan = this.scheduledPlan;
            this.scheduledPlan = null;
        }
    }

    public SubscriptionPlan getNextBillingPlan() {
        return this.scheduledPlan != null ? this.scheduledPlan : this.subscriptionPlan;
    }

    public void markAsPastDue() {
        markAsPastDue(Instant.now());
    }

    public void markAsPastDue(Instant occurredAt) {
        this.status = SubscriptionStatus.PAST_DUE;
        if (this.pastDueAt == null) {
            this.pastDueAt = occurredAt;
        }
        this.currentMarker = true;
    }

    public Instant getGracePeriodEndsAt() {
        if (pastDueAt == null) {
            return null;
        }
        return pastDueAt.plus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
    }

    public boolean isGracePeriodExpired(Instant now) {
        Instant gracePeriodEndsAt = getGracePeriodEndsAt();
        return gracePeriodEndsAt != null && !now.isBefore(gracePeriodEndsAt);
    }

    public void markAsUnpaid() {
        this.status = SubscriptionStatus.UNPAID;
        this.currentMarker = true;
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }

    public boolean isCanceled() {
        return status == SubscriptionStatus.CANCELED;
    }

    public boolean isPastDue() {
        return status == SubscriptionStatus.PAST_DUE;
    }

    public boolean isUnpaid() {
        return status == SubscriptionStatus.UNPAID;
    }

    public boolean isCurrent() {
        return Boolean.TRUE.equals(currentMarker);
    }

    public void expire() {
        this.status = SubscriptionStatus.EXPIRED;
        this.currentMarker = null;
    }

    public void extendPeriod() {
        applyScheduledPlanIfPresent();
        this.status = SubscriptionStatus.ACTIVE;
        this.pastDueAt = null;
        LocalDateTime now = LocalDateTime.now();
        this.currentPeriodStart = now;
        this.currentPeriodEnd = calculatePeriodEnd(now);
    }
}
