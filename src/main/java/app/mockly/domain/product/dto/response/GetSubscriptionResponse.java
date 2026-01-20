package app.mockly.domain.product.dto.response;

import app.mockly.domain.product.dto.PlanSnapshot;
import app.mockly.domain.product.entity.Subscription;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GetSubscriptionResponse(
        Long id,
        String status,
        LocalDateTime startedAt,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        LocalDateTime nextBillingDate,
        BigDecimal nextBillingAmount,
        PlanSnapshot planSnapshot
) {
    public static GetSubscriptionResponse from(Subscription subscription) {
        PlanSnapshot planSnapshot = PlanSnapshot.from(subscription);

        return new GetSubscriptionResponse(
                subscription.getId(),
                subscription.getStatus().name(),
                subscription.getStartedAt(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getCurrentPeriodEnd(),  // nextBillingDate
                planSnapshot.price(),
                planSnapshot
        );
    }
}
