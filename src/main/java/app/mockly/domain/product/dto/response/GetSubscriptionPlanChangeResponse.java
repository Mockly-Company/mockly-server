package app.mockly.domain.product.dto.response;

import app.mockly.domain.product.entity.SubscriptionPlan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GetSubscriptionPlanChangeResponse(
        PlanPreview currentPlan,
        PlanPreview targetPlan,
        BigDecimal immediateAmount,
        BigDecimal nextBillingAmount,
        LocalDateTime nextBillingDate
) {
    public record PlanPreview(
            Integer id,
            String name,
            BigDecimal price,
            String billingCycle,
            List<String> features
    ) {
        public static PlanPreview from(SubscriptionPlan plan) {
            List<String> features = plan.getProduct().getFeatures() != null
                    ? List.copyOf(plan.getProduct().getFeatures())
                    : List.of();
            return new PlanPreview(
                    plan.getId(),
                    plan.getProduct().getName(),
                    normalizeAmount(plan.getPrice()),
                    plan.getBillingCycle().name(),
                    features
            );
        }
    }

    public static GetSubscriptionPlanChangeResponse of(
            SubscriptionPlan currentPlan,
            SubscriptionPlan targetPlan,
            BigDecimal immediateAmount,
            BigDecimal nextBillingAmount,
            LocalDateTime nextBillingDate
    ) {
        return new GetSubscriptionPlanChangeResponse(
                PlanPreview.from(currentPlan),
                PlanPreview.from(targetPlan),
                normalizeAmount(immediateAmount),
                normalizeAmount(nextBillingAmount),
                nextBillingDate
        );
    }

    private static BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.stripTrailingZeros().scale() <= 0
                ? BigDecimal.valueOf(value.longValue())
                : value.stripTrailingZeros();
    }
}
