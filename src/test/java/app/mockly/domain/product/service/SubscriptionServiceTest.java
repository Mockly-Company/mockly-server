package app.mockly.domain.product.service;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.entity.OutboxEventStatus;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.InvoiceRepository;
import app.mockly.domain.payment.repository.OutboxEventRepository;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
import app.mockly.domain.payment.repository.PaymentRepository;
import app.mockly.domain.product.dto.request.CreateSubscriptionPlanChangeRequest;
import app.mockly.domain.product.dto.response.CancelSubscriptionResponse;
import app.mockly.domain.product.dto.response.CreateSubscriptionPlanChangeResponse;
import app.mockly.domain.product.dto.response.GetSubscriptionPlanChangeResponse;
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
import app.mockly.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

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

    @Test
    void assignFreePlan_doesNotReplaceAnUnpaidCurrentSubscription() {
        User user = userRepository.save(User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("unpaid-subscription-provider-id")
                .email("unpaid-subscription@example.com")
                .name("미납 구독 사용자")
                .build());

        SubscriptionProduct basicProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Basic unpaid")
                .planTier(PlanTier.BASIC)
                .isActive(true)
                .maxQuestions(5)
                .weeklyInterviewLimit(4)
                .weeklyImprovementPracticeLimit(0)
                .build());
        SubscriptionPlan basicPlan = planRepository.save(SubscriptionPlan.builder()
                .product(basicProduct)
                .price(new BigDecimal("5900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());
        Subscription existing = Subscription.create(user.getId(), basicPlan);
        existing.activate();
        existing.markAsPastDue(Instant.parse("2026-08-01T00:00:00Z"));
        existing.markAsUnpaid();
        subscriptionRepository.save(existing);

        SubscriptionProduct freeProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Free for unpaid guard")
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

        assertThat(subscriptionRepository.findAll())
                .filteredOn(subscription -> subscription.getUserId().equals(user.getId()))
                .singleElement()
                .extracting(Subscription::getStatus)
                .isEqualTo(SubscriptionStatus.UNPAID);
    }

    @Test
    void cancelSubscription_unpaidImmediatelyTransitionsToFreeInSameTransaction() {
        User user = userRepository.save(User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("cancel-unpaid-provider")
                .email("cancel-unpaid@example.com")
                .name("연체 해지 사용자")
                .build());

        SubscriptionProduct freeProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Free")
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

        SubscriptionProduct basicProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Basic")
                .planTier(PlanTier.BASIC)
                .isActive(true)
                .maxQuestions(5)
                .weeklyInterviewLimit(4)
                .weeklyImprovementPracticeLimit(0)
                .build());
        SubscriptionPlan basicPlan = planRepository.save(SubscriptionPlan.builder()
                .product(basicProduct)
                .price(new BigDecimal("5900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());

        Subscription unpaid = Subscription.create(user.getId(), basicPlan);
        unpaid.activate();
        unpaid.markAsPastDue(Instant.parse("2026-08-01T00:00:00Z"));
        unpaid.markAsUnpaid();
        unpaid = subscriptionRepository.saveAndFlush(unpaid);

        CancelSubscriptionResponse response = subscriptionService.cancelSubscription(user.getId(), unpaid.getId(), false);

        assertThat(response.status()).isEqualTo("CANCELED");
        assertThat(response.cancelAtPeriodEnd()).isFalse();

        Subscription current = subscriptionRepository.findCurrentByUserId(user.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(current.getSubscriptionPlan().getId()).isEqualTo(freePlan.getId());
    }

    @Test
    void updatePaymentMethod_recoversPastDueSubscriptionAndSchedulesNextBilling() {
        User user = userRepository.save(User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("recover-past-due-provider")
                .email("recover-past-due@example.com")
                .name("연체 복구 사용자")
                .build());

        SubscriptionProduct basicProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Basic")
                .planTier(PlanTier.BASIC)
                .isActive(true)
                .maxQuestions(5)
                .weeklyInterviewLimit(4)
                .weeklyImprovementPracticeLimit(0)
                .build());
        SubscriptionPlan basicPlan = planRepository.save(SubscriptionPlan.builder()
                .product(basicProduct)
                .price(new BigDecimal("5900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());

        Subscription pastDue = Subscription.create(user.getId(), basicPlan);
        pastDue.activate();
        pastDue.markAsPastDue(Instant.parse("2026-08-10T00:00:00Z"));
        pastDue = subscriptionRepository.saveAndFlush(pastDue);

        PaymentMethod newMethod = paymentMethodRepository.save(
                PaymentMethod.create(user, "recovery_billing_key_1234", "9876****1234", "VISA", true)
        );
        given(portOneService.createPaymentSchedule(anyString(), anyString(), anyString(), any(), any(), any()))
                .willReturn("schedule_after_recovery");

        subscriptionService.updatePaymentMethod(user.getId(), pastDue.getId(), newMethod.getId());

        Subscription recovered = subscriptionRepository.findByIdWithDetails(pastDue.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(recovered.getPastDueAt()).isNull();
        assertThat(recovered.getPaymentMethod().getId()).isEqualTo(newMethod.getId());
        assertThat(recovered.getCurrentPaymentScheduleId()).isEqualTo("schedule_after_recovery");
        verify(portOneService).payWithBillingKey(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void renewSubscription_appliesScheduledDowngradePlan() {
        User user = userRepository.save(User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("downgrade-renew-provider")
                .email("downgrade-renew@example.com")
                .name("다운그레이드 사용자")
                .build());

        SubscriptionProduct basicProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Basic")
                .planTier(PlanTier.BASIC)
                .features(List.of("AI 면접 연습 주 4회"))
                .isActive(true)
                .maxQuestions(5)
                .weeklyInterviewLimit(4)
                .weeklyImprovementPracticeLimit(0)
                .build());
        SubscriptionPlan basicPlan = planRepository.save(SubscriptionPlan.builder()
                .product(basicProduct)
                .price(new BigDecimal("5900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());

        SubscriptionProduct proProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Pro")
                .planTier(PlanTier.PRO)
                .features(List.of("AI 면접 연습 주 10회", "개선 연습 주 4회"))
                .isActive(true)
                .maxQuestions(7)
                .weeklyInterviewLimit(10)
                .weeklyImprovementPracticeLimit(4)
                .build());
        SubscriptionPlan proPlan = planRepository.save(SubscriptionPlan.builder()
                .product(proProduct)
                .price(new BigDecimal("9900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());

        PaymentMethod method = paymentMethodRepository.save(
                PaymentMethod.create(user, "downgrade_billing_key", "1111****2222", "VISA", true)
        );
        Subscription proSub = Subscription.create(user.getId(), proPlan, method);
        proSub.activate();
        proSub = subscriptionRepository.saveAndFlush(proSub);

        GetSubscriptionPlanChangeResponse preview = subscriptionService.previewPlanChange(user.getId(), basicPlan.getId());
        assertThat(preview.immediateAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(preview.nextBillingAmount()).isEqualByComparingTo(new BigDecimal("5900"));

        CreateSubscriptionPlanChangeResponse changeRes = subscriptionService.changeSubscriptionPlan(
                user.getId(),
                new CreateSubscriptionPlanChangeRequest(basicPlan.getId(), BigDecimal.ZERO)
        );
        assertThat(changeRes.chargedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(changeRes.subscription().planSnapshot().name()).isEqualTo("Pro");
        assertThat(changeRes.subscription().scheduledPlanName()).isEqualTo("Basic");

        given(portOneService.createPaymentSchedule(anyString(), anyString(), anyString(), any(), any(), any()))
                .willReturn("next_basic_schedule");
        subscriptionService.renewSubscription(proSub, method.getBillingKey());

        Subscription renewed = subscriptionRepository.findByIdWithDetails(proSub.getId()).orElseThrow();
        assertThat(renewed.getSubscriptionPlan().getId()).isEqualTo(basicPlan.getId());
        assertThat(renewed.getScheduledPlan()).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void updatePaymentMethod_delinquentRecoveryFailure_runsOutsideTxAndKeepsExistingSchedule() {
        User user = userRepository.save(User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("non-tx-delinquent-provider")
                .email("non-tx-delinquent@example.com")
                .name("트랜잭션 분리 연체 사용자")
                .build());
        SubscriptionProduct basicProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Basic NonTx")
                .planTier(PlanTier.BASIC)
                .isActive(true)
                .maxQuestions(5)
                .weeklyInterviewLimit(4)
                .weeklyImprovementPracticeLimit(0)
                .build());
        SubscriptionPlan basicPlan = planRepository.save(SubscriptionPlan.builder()
                .product(basicProduct)
                .price(new BigDecimal("5900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());
        Subscription pastDue = Subscription.create(user.getId(), basicPlan);
        pastDue.activate();
        pastDue.markAsPastDue(Instant.parse("2026-08-10T00:00:00Z"));
        pastDue.setCurrentPaymentScheduleId("existing_portone_schedule_id");
        pastDue = subscriptionRepository.save(pastDue);

        PaymentMethod newMethod = paymentMethodRepository.save(
                PaymentMethod.create(user, "non_tx_recovery_billing_key", "9876****1234", "VISA", true)
        );

        Long subscriptionId = pastDue.getId();
        AtomicBoolean txActiveDuringPortOneCall = new AtomicBoolean(true);

        try {
            doAnswer(invocation -> {
                txActiveDuringPortOneCall.set(TransactionSynchronizationManager.isActualTransactionActive());
                throw new RuntimeException("PortOne timeout");
            }).when(portOneService).payWithBillingKey(anyString(), anyString(), anyString(), any(), any());

            assertThatThrownBy(() -> subscriptionService.updatePaymentMethod(user.getId(), subscriptionId, newMethod.getId()))
                    .isInstanceOf(BusinessException.class);

            assertThat(txActiveDuringPortOneCall.get()).isFalse();
            verify(portOneService, never()).revokePaymentSchedule(anyString());

            Subscription unchanged = subscriptionRepository.findById(subscriptionId).orElseThrow();
            assertThat(unchanged.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
            assertThat(unchanged.getCurrentPaymentScheduleId()).isEqualTo("existing_portone_schedule_id");
        } finally {
            paymentRepository.deleteAll();
            invoiceRepository.deleteAll();
            outboxEventRepository.deleteAll();
            subscriptionRepository.deleteById(subscriptionId);
            paymentMethodRepository.deleteById(newMethod.getId());
            planRepository.deleteById(basicPlan.getId());
            productRepository.deleteById(basicProduct.getId());
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void changeSubscriptionPlan_upgradeRunsPortOneOutsideTxAndRetainsPendingOutboxWhenScheduleFails() {
        User user = userRepository.save(User.builder()
                .provider(OAuth2Provider.GOOGLE)
                .providerId("non-tx-upgrade-provider")
                .email("non-tx-upgrade@example.com")
                .name("트랜잭션 분리 업그레이드 사용자")
                .build());
        SubscriptionProduct basicProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Basic Upgrade NonTx")
                .planTier(PlanTier.BASIC)
                .isActive(true)
                .maxQuestions(5)
                .weeklyInterviewLimit(4)
                .weeklyImprovementPracticeLimit(0)
                .build());
        SubscriptionPlan basicPlan = planRepository.save(SubscriptionPlan.builder()
                .product(basicProduct)
                .price(new BigDecimal("5900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());
        SubscriptionProduct proProduct = productRepository.save(SubscriptionProduct.builder()
                .name("Pro Upgrade NonTx")
                .planTier(PlanTier.PRO)
                .isActive(true)
                .maxQuestions(7)
                .weeklyInterviewLimit(10)
                .weeklyImprovementPracticeLimit(4)
                .build());
        SubscriptionPlan proPlan = planRepository.save(SubscriptionPlan.builder()
                .product(proProduct)
                .price(new BigDecimal("9900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());
        PaymentMethod method = paymentMethodRepository.save(
                PaymentMethod.create(user, "non_tx_upgrade_billing_key", "1111****2222", "VISA", true)
        );
        Subscription activeBasic = Subscription.create(user.getId(), basicPlan, method);
        activeBasic.activate();
        activeBasic = subscriptionRepository.save(activeBasic);

        Long subscriptionId = activeBasic.getId();
        AtomicBoolean txActiveDuringPay = new AtomicBoolean(true);

        try {
            doAnswer(invocation -> {
                txActiveDuringPay.set(TransactionSynchronizationManager.isActualTransactionActive());
                return null;
            }).when(portOneService).payWithBillingKey(anyString(), anyString(), anyString(), any(), any());

            given(portOneService.createPaymentSchedule(anyString(), anyString(), anyString(), any(), any(), any()))
                    .willThrow(new RuntimeException("PortOne schedule API transient error"));

            GetSubscriptionPlanChangeResponse preview = subscriptionService.previewPlanChange(user.getId(), proPlan.getId());
            CreateSubscriptionPlanChangeResponse response = subscriptionService.changeSubscriptionPlan(
                    user.getId(),
                    new CreateSubscriptionPlanChangeRequest(proPlan.getId(), preview.immediateAmount())
            );

            assertThat(txActiveDuringPay.get()).isFalse();
            assertThat(response.subscription().planSnapshot().name()).isEqualTo("Pro Upgrade NonTx");
            assertThat(outboxEventRepository.findByAggregateIdAndEventTypeAndStatus(
                    subscriptionId,
                    "SCHEDULE_CREATE",
                    OutboxEventStatus.PENDING
            )).isPresent();
        } finally {
            paymentRepository.deleteAll();
            invoiceRepository.deleteAll();
            outboxEventRepository.deleteAll();
            subscriptionRepository.deleteById(subscriptionId);
            paymentMethodRepository.deleteById(method.getId());
            planRepository.deleteById(proPlan.getId());
            planRepository.deleteById(basicPlan.getId());
            productRepository.deleteById(proProduct.getId());
            productRepository.deleteById(basicProduct.getId());
            userRepository.deleteById(user.getId());
        }
    }
}
