package app.mockly.domain.product.dto;

import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionPlan;

import java.math.BigDecimal;

public record PlanSnapshot(
        Integer id,
        String name,
        BigDecimal price,
        String billingCycle
) {
    public static PlanSnapshot from(Subscription subscription) {
        SubscriptionPlan subscriptionPlan = subscription.getSubscriptionPlan();

        return new PlanSnapshot(
                subscriptionPlan.getId(),
                subscriptionPlan.getProduct().getName(),
                subscriptionPlan.getAmount(),
                subscriptionPlan.getBillingCycle().name()
        );
    }
}
