package app.mockly.domain.product.dto.response;

import app.mockly.domain.product.dto.PlanSnapshot;
import app.mockly.domain.product.entity.Subscription;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateSubscriptionResponse(
    Long id,
    String status,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    LocalDateTime nextBillingDate,
    BigDecimal nextBillingAmount,
    PlanSnapshot planSnapshot
) {
    public static CreateSubscriptionResponse from(Subscription subscription) {
        PlanSnapshot planSnapshot = PlanSnapshot.from(subscription);
        return new CreateSubscriptionResponse(
                subscription.getId(),
                subscription.getStatus().toString(),
                subscription.getStartedAt(),
                subscription.getCurrentPeriodEnd().minusDays(1).withHour(23).withMinute(59).withSecond(59),
                subscription.getCurrentPeriodEnd(),
                planSnapshot.price(),
                planSnapshot
        );
    }
}
