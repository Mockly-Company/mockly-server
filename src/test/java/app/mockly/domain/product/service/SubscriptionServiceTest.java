package app.mockly.domain.product.service;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.product.entity.BillingCycle;
import app.mockly.domain.product.entity.Currency;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionPlan;
import app.mockly.domain.product.entity.SubscriptionProduct;
import app.mockly.domain.product.entity.SubscriptionStatus;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SubscriptionServiceTest {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionProductRepository productRepository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @MockitoBean
    private PortOneService portOneService;

    @Test
    void assignFreePlan_createsActiveMonthlySubscriptionWithRealId() {
        User user = userRepository.save(User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("free-user-provider-id")
                .email("free-user@example.com")
                .name("무료 사용자")
                .build());

        SubscriptionProduct freeProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Free")
                .planTier(PlanTier.FREE)
                .isActive(true)
                .maxQuestions(3)
                .weeklyInterviewLimit(1)
                .weeklyImprovementPracticeLimit(0)
                .build());
        planRepository.save(SubscriptionPlan.builder()
                .product(freeProduct)
                .price(BigDecimal.ZERO)
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());

        subscriptionService.assignFreePlan(user.getId());

        Subscription subscription = subscriptionRepository
                .findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE)
                .orElseThrow();
        assertThat(subscription.getId()).isNotNull();
        assertThat(subscription.getSubscriptionPlan().getProduct().getPlanTier()).isEqualTo(PlanTier.FREE);
        assertThat(subscription.getSubscriptionPlan().getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(subscription.getCurrentPeriodStart()).isNotNull();
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(subscription.getCurrentPeriodStart().plusMonths(1));
    }

    @Test
    void assignFreePlan_doesNotCreateSecondActiveSubscription() {
        User user = userRepository.save(User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("existing-subscription-provider-id")
                .email("existing-subscription@example.com")
                .name("기존 구독 사용자")
                .build());

        SubscriptionProduct freeProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Free duplicate guard")
                .planTier(PlanTier.FREE)
                .isActive(true)
                .maxQuestions(3)
                .weeklyInterviewLimit(1)
                .weeklyImprovementPracticeLimit(0)
                .build());
        SubscriptionPlan freePlan = planRepository.save(SubscriptionPlan.builder()
                .product(freeProduct)
                .price(BigDecimal.ZERO)
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());
        Subscription existing = Subscription.create(user.getId(), freePlan);
        existing.activate();
        subscriptionRepository.save(existing);

        subscriptionService.assignFreePlan(user.getId());

        long activeCount = subscriptionRepository.findAll().stream()
                .filter(subscription -> subscription.getUserId().equals(user.getId()))
                .filter(subscription -> subscription.getStatus() == SubscriptionStatus.ACTIVE)
                .count();
        assertThat(activeCount).isEqualTo(1);
    }
}
