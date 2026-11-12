package app.mockly.domain.product.dto.response;

import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionPlan;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateSubscriptionPlanChangeResponse(
        BigDecimal chargedAmount,
        String currency,
        LocalDateTime requestedAt,
        LocalDateTime effectiveAt,
        ChangedSubscriptionInfo subscription
) {
    public record ChangedSubscriptionInfo(
            Long id,
            String status,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime nextBillingDate,
            BigDecimal nextBillingAmount,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String scheduledPlanName,
            ChangedPlanSnapshot planSnapshot
    ) {
    }

    public record ChangedPlanSnapshot(
            Integer id,
            String name,
            BigDecimal price,
            String billingCycle,
            List<String> features
    ) {
        public static ChangedPlanSnapshot from(SubscriptionPlan plan) {
            List<String> features = plan.getProduct().getFeatures() != null
                    ? List.copyOf(plan.getProduct().getFeatures())
                    : List.of();
            return new ChangedPlanSnapshot(
                    plan.getId(),
                    plan.getProduct().getName(),
                    normalizeAmount(plan.getPrice()),
                    plan.getBillingCycle().name(),
                    features
            );
        }
    }

    public static CreateSubscriptionPlanChangeResponse of(
            Subscription subscription,
            BigDecimal chargedAmount,
            String currency,
            LocalDateTime requestedAt,
            LocalDateTime effectiveAt
    ) {
        SubscriptionPlan activePlan = subscription.getSubscriptionPlan();
        SubscriptionPlan nextPlan = subscription.getNextBillingPlan();
        String scheduledPlanName = subscription.getScheduledPlan() != null
                ? subscription.getScheduledPlan().getProduct().getName()
                : null;
        // 이용 종료 시각은 CancelSubscriptionResponse.availableUntil과 동일한 end-exclusive 기준(currentPeriodEnd) 사용
        LocalDateTime endedAt = subscription.getCurrentPeriodEnd();

        return new CreateSubscriptionPlanChangeResponse(
                normalizeAmount(chargedAmount),
                currency,
                requestedAt,
                effectiveAt,
                new ChangedSubscriptionInfo(
                        subscription.getId(),
                        subscription.getStatus().name(),
                        subscription.getStartedAt(),
                        endedAt,
                        subscription.getCurrentPeriodEnd(),
                        normalizeAmount(nextPlan.getPrice()),
                        scheduledPlanName,
                        ChangedPlanSnapshot.from(activePlan)
                )
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
