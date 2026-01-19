package app.mockly.domain.product.dto;

import app.mockly.domain.product.entity.BillingCycle;
import app.mockly.domain.product.entity.Currency;
import app.mockly.domain.product.entity.SubscriptionPlan;

import java.math.BigDecimal;

public record SubscriptionPlanInfo(
        Integer id,
        BigDecimal price,
        Currency currency,
        BillingCycle billingCycle,
        boolean isActive
) {
    public static SubscriptionPlanInfo from(SubscriptionPlan plan, Integer activePlanId) {
        boolean isActive = activePlanId != null && activePlanId.equals(plan.getId());
        return new SubscriptionPlanInfo(
                plan.getId(),
                plan.getPrice(),
                plan.getCurrency(),
                plan.getBillingCycle(),
                isActive
        );
    }
}
