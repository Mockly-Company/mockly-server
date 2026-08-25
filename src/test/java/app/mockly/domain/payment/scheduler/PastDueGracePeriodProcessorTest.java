package app.mockly.domain.payment.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.product.entity.BillingCycle;
import app.mockly.domain.product.entity.Currency;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionPlan;
import app.mockly.domain.product.entity.SubscriptionProduct;
import app.mockly.domain.product.entity.SubscriptionStatus;
import app.mockly.domain.product.repository.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PastDueGracePeriodProcessorTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PortOneService portOneService;

    @InjectMocks
    private PastDueGracePeriodProcessor processor;

    @Test
    void processExpiredPastDueSubscriptions_marksTheSubscriptionUnpaidUsingPastDueAt() {
        Subscription subscription = paidSubscription();
        subscription.markAsPastDue(Instant.now().minus(8, ChronoUnit.DAYS));
        subscription.setCurrentPaymentScheduleId("schedule-id");
        given(subscriptionRepository.findByStatusAndPastDueAtLessThanEqual(
                eq(SubscriptionStatus.PAST_DUE), any(Instant.class)))
                .willReturn(List.of(subscription));

        processor.processExpiredPastDueSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.UNPAID);
        assertThat(subscription.isCurrent()).isTrue();
        assertThat(subscription.getCurrentPaymentScheduleId()).isNull();
        verify(portOneService).revokePaymentSchedule("schedule-id");
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
