package app.mockly.domain.product.dto.response;

import app.mockly.domain.product.entity.Subscription;

import java.time.LocalDateTime;

public record CancelSubscriptionResponse(
        Long id,
        String status,
        LocalDateTime canceledAt,
        LocalDateTime availableUntil
){
    public static CancelSubscriptionResponse from(Subscription subscription) {
        return new CancelSubscriptionResponse(
                subscription.getId(),
                subscription.getStatus().name(),
                subscription.getCanceledAt(),
                subscription.getCurrentPeriodEnd()
        );
    }
}
