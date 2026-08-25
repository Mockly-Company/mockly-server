package app.mockly.domain.product.dto.response;

import app.mockly.domain.product.dto.PlanSnapshot;
import app.mockly.domain.product.entity.Subscription;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;

public record GetSubscriptionResponse(
        Long id,
        String status,
        LocalDateTime startedAt,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        Instant pastDueAt,
        Instant gracePeriodEndsAt,
        LocalDateTime nextBillingDate,
        BigDecimal nextBillingAmount,
        PlanSnapshot planSnapshot
) {
    public static GetSubscriptionResponse from(Subscription subscription) {
        PlanSnapshot planSnapshot = PlanSnapshot.from(subscription);

        boolean billingScheduled = subscription.isActive();
        return new GetSubscriptionResponse(
                subscription.getId(),
                subscription.getStatus().name(),
                subscription.getStartedAt(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getPastDueAt(),
                subscription.getGracePeriodEndsAt(),
                billingScheduled ? subscription.getCurrentPeriodEnd() : null,
                billingScheduled ? planSnapshot.price() : null,
                planSnapshot
        );
    }
}
