package app.mockly.domain.product.dto.response;

import app.mockly.domain.product.entity.Subscription;

import java.time.LocalDateTime;

public record CancelSubscriptionResponse(
        Long id,
        String status,
        boolean cancelAtPeriodEnd,
        LocalDateTime canceledAt,
        LocalDateTime availableUntil
) {
    public static CancelSubscriptionResponse from(Subscription subscription) {
        LocalDateTime availableUntil = subscription.isCancelAtPeriodEnd()
                ? subscription.getCurrentPeriodEnd()
                : subscription.getCanceledAt();
        return new CancelSubscriptionResponse(
                subscription.getId(),
                subscription.getStatus().name(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getCanceledAt(),
                availableUntil
        );
    }
}
