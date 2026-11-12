package app.mockly.domain.product.dto.response;

import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.product.dto.PlanSnapshot;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionPlan;
import app.mockly.global.security.SensitiveDataMasker;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record GetSubscriptionResponse(
        Long id,
        String status,
        boolean cancelAtPeriodEnd,
        LocalDateTime startedAt,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        Instant pastDueAt,
        Instant gracePeriodEndsAt,
        LocalDateTime nextBillingDate,
        BigDecimal nextBillingAmount,
        PlanSnapshot planSnapshot,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String scheduledPlanName,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String cardBrand,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String cardNumber
) {
    public static GetSubscriptionResponse from(Subscription subscription) {
        return from(subscription, subscription.getPaymentMethod());
    }

    public static GetSubscriptionResponse from(Subscription subscription, PaymentMethod paymentMethod) {
        PlanSnapshot planSnapshot = PlanSnapshot.from(subscription);
        SubscriptionPlan nextBillingPlan = subscription.getNextBillingPlan();

        boolean billingScheduled = subscription.isActive()
                && !subscription.isCancelAtPeriodEnd()
                && !subscription.getSubscriptionPlan().isFree();

        String scheduledPlanName = subscription.getScheduledPlan() != null
                ? subscription.getScheduledPlan().getProduct().getName()
                : null;
        String cardBrand = paymentMethod != null ? paymentMethod.getCardBrand() : null;
        String cardNumber = paymentMethod != null
                ? SensitiveDataMasker.maskCardNumber(paymentMethod.getCardNumber())
                : null;

        return new GetSubscriptionResponse(
                subscription.getId(),
                subscription.getStatus().name(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getStartedAt(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getPastDueAt(),
                subscription.getGracePeriodEndsAt(),
                billingScheduled ? subscription.getCurrentPeriodEnd() : null,
                billingScheduled ? normalizeAmount(nextBillingPlan.getPrice()) : null,
                planSnapshot,
                scheduledPlanName,
                cardBrand,
                cardNumber
        );
    }

    private static BigDecimal normalizeAmount(BigDecimal price) {
        if (price == null) {
            return null;
        }
        return price.stripTrailingZeros().scale() <= 0
                ? BigDecimal.valueOf(price.longValue())
                : price.stripTrailingZeros();
    }
}
