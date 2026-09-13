package app.mockly.domain.product.dto;

import app.mockly.domain.product.entity.BillingCycle;
import app.mockly.domain.product.entity.Currency;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.entity.SubscriptionProduct;

import java.util.List;

public record SubscriptionProductInfo(
        Integer id,
        String name,
        String description,
        PlanTier planTier,
        int maxQuestions,
        int weeklyInterviewLimit,
        int weeklyImprovementPracticeLimit,
        List<String> features,
        List<SubscriptionPlanInfo> plans
) {
    public static SubscriptionProductInfo from(SubscriptionProduct product, Integer activePlanId) {
        List<SubscriptionPlanInfo> plans = product.getSubscriptionPlans().stream()
                .filter(plan -> plan.getBillingCycle() == BillingCycle.MONTHLY)
                .filter(plan -> plan.getCurrency() == Currency.KRW)
                .map(plan -> SubscriptionPlanInfo.from(plan, activePlanId))
                .toList();

        return new SubscriptionProductInfo(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPlanTier(),
                product.getMaxQuestions(),
                product.getWeeklyInterviewLimit(),
                product.getWeeklyImprovementPracticeLimit(),
                product.getFeatures(),
                plans
        );
    }
}
