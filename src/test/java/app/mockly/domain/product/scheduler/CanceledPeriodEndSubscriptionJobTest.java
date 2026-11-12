package app.mockly.domain.product.scheduler;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.product.entity.*;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CanceledPeriodEndSubscriptionJobTest {

    @Autowired
    private CanceledPeriodEndSubscriptionJob job;

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

    private SubscriptionPlan freePlan;
    private SubscriptionPlan basicPlan;

    @BeforeEach
    void setUp() {
        SubscriptionProduct freeProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Free")
                .planTier(PlanTier.FREE)
                .isActive(true)
                .maxQuestions(3)
                .weeklyInterviewLimit(1)
                .weeklyImprovementPracticeLimit(0)
                .build());
        freePlan = planRepository.save(SubscriptionPlan.builder()
                .product(freeProduct)
                .price(BigDecimal.ZERO)
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());

        SubscriptionProduct basicProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Basic")
                .planTier(PlanTier.BASIC)
                .isActive(true)
                .maxQuestions(5)
                .weeklyInterviewLimit(4)
                .weeklyImprovementPracticeLimit(0)
                .build());
        basicPlan = planRepository.save(SubscriptionPlan.builder()
                .product(basicProduct)
                .price(new BigDecimal("5900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());
    }

    @Test
    @DisplayName("해지 예약된 구독의 이용 기간이 만료되면 CANCELED로 전환하고 Free 플랜을 자동 배정한다")
    void processExpiredCancelAtPeriodEndSubscriptions_transitionsToFree() {
        User user = userRepository.save(User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("expired-cancel-user")
                .email("expired-cancel@example.com")
                .name("만료 해지 사용자")
                .build());

        Subscription paidSubscription = Subscription.create(user.getId(), basicPlan);
        paidSubscription.activate();
        paidSubscription.scheduleCancelAtPeriodEnd();
        LocalDateTime expiredEnd = LocalDateTime.now().minusHours(2);
        ReflectionTestUtils.setField(paidSubscription, "currentPeriodEnd", expiredEnd);
        paidSubscription = subscriptionRepository.saveAndFlush(paidSubscription);

        job.processExpiredCancelAtPeriodEndSubscriptions(LocalDateTime.now());

        Subscription canceled = subscriptionRepository.findById(paidSubscription.getId()).orElseThrow();
        assertThat(canceled.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(canceled.getCurrentMarker()).isNull();

        Subscription current = subscriptionRepository.findCurrentByUserId(user.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(current.getSubscriptionPlan().getId()).isEqualTo(freePlan.getId());
    }

    @Test
    @DisplayName("아직 이용 기간이 남아있는 해지 예약 구독은 ACTIVE 상태를 유지한다")
    void processExpiredCancelAtPeriodEndSubscriptions_keepsFutureSubscriptionActive() {
        User user = userRepository.save(User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("future-cancel-user")
                .email("future-cancel@example.com")
                .name("유예 해지 사용자")
                .build());

        Subscription paidSubscription = Subscription.create(user.getId(), basicPlan);
        paidSubscription.activate();
        paidSubscription.scheduleCancelAtPeriodEnd();
        paidSubscription = subscriptionRepository.saveAndFlush(paidSubscription);

        job.processExpiredCancelAtPeriodEndSubscriptions(LocalDateTime.now());

        Subscription reloaded = subscriptionRepository.findById(paidSubscription.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(reloaded.isCancelAtPeriodEnd()).isTrue();
        assertThat(reloaded.isCurrent()).isTrue();
    }
}
