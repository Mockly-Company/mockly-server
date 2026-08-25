package app.mockly.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    @Test
    void markAsPastDue_recordsTheFirstFailureAndCalculatesSevenDayGracePeriod() {
        Subscription subscription = paidSubscription();
        Instant firstFailureAt = Instant.parse("2026-08-25T03:00:00Z");

        subscription.markAsPastDue(firstFailureAt);
        subscription.markAsPastDue(firstFailureAt.plus(1, ChronoUnit.DAYS));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.getPastDueAt()).isEqualTo(firstFailureAt);
        assertThat(subscription.getGracePeriodEndsAt()).isEqualTo(firstFailureAt.plus(7, ChronoUnit.DAYS));
        assertThat(subscription.isGracePeriodExpired(firstFailureAt.plus(7, ChronoUnit.DAYS))).isTrue();
    }

    @Test
    void markAsUnpaid_keepsTheSubscriptionCurrentButRemovesAccess() {
        Subscription subscription = paidSubscription();
        subscription.markAsPastDue(Instant.parse("2026-08-25T03:00:00Z"));

        subscription.markAsUnpaid();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.UNPAID);
        assertThat(subscription.isCurrent()).isTrue();
    }

    private Subscription paidSubscription() {
        SubscriptionProduct product = SubscriptionProduct.builder()
                .name("Basic")
                .planTier(PlanTier.BASIC)
                .isActive(true)
                .build();
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .product(product)
                .price(new BigDecimal("5900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build();
        Subscription subscription = Subscription.create(UUID.randomUUID(), plan);
        subscription.activate();
        return subscription;
    }
}
