package app.mockly.domain.product.dto;

import app.mockly.domain.product.entity.SubscriptionProduct;

import java.util.List;

public record SubscriptionProductInfo(
        Integer id,
        String name,
        String description,
        List<String> features,
        List<SubscriptionPlanInfo> plans
) {
    public static SubscriptionProductInfo from(SubscriptionProduct product, Integer activePlanId) {
        List<SubscriptionPlanInfo> plans = product.getSubscriptionPlans().stream()
                .map(plan -> SubscriptionPlanInfo.from(plan, activePlanId))
                .toList();

        return new SubscriptionProductInfo(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getFeatures(),
                plans
        );
    }
}
