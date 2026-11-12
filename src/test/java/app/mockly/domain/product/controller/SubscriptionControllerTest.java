package app.mockly.domain.product.controller;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
import app.mockly.domain.product.controller.docs.SubscriptionDocs;
import app.mockly.domain.product.dto.request.CancelSubscriptionRequest;
import app.mockly.domain.product.dto.request.CreateSubscriptionPlanChangeRequest;
import app.mockly.domain.product.dto.request.CreateSubscriptionRequest;
import app.mockly.domain.product.dto.request.UpdatePaymentMethodRequest;
import app.mockly.domain.product.entity.*;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private PortOneService portOneService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionProductRepository subscriptionProductRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    private User testUser;
    private String validAccessToken;
    private SubscriptionPlan freePlan;
    private SubscriptionPlan basicMonthlyPlan;
    private SubscriptionPlan proMonthlyPlan;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .name("테스트 사용자")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("test-google-provider-id")
                .build();
        testUser = userRepository.save(testUser);

        validAccessToken = jwtService.generateAccessToken(testUser.getId());
        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);
        given(portOneService.createPaymentSchedule(anyString(), anyString(), anyString(), any(), any(), any()))
                .willReturn("mock_schedule_id");

        SubscriptionProduct freeProduct = subscriptionProductRepository.save(SubscriptionProduct.builder()
                .name("Free")
                .description("무료 플랜")
                .features(List.of("AI 면접 연습 주 1회", "기본 면접 히스토리 조회"))
                .planTier(PlanTier.FREE)
                .maxQuestions(3)
                .weeklyInterviewLimit(1)
                .weeklyImprovementPracticeLimit(0)
                .isActive(true)
                .build());

        freePlan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .product(freeProduct)
                .price(BigDecimal.ZERO)
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());

        SubscriptionProduct basicProduct = subscriptionProductRepository.save(SubscriptionProduct.builder()
                .name("Basic")
                .description("베이직 플랜")
                .features(List.of("AI 면접 연습 주 4회", "AI 상세 피드백 제공"))
                .planTier(PlanTier.BASIC)
                .maxQuestions(5)
                .weeklyInterviewLimit(4)
                .weeklyImprovementPracticeLimit(0)
                .isActive(true)
                .build());

        basicMonthlyPlan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .product(basicProduct)
                .price(new BigDecimal("5900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());

        SubscriptionProduct proProduct = subscriptionProductRepository.save(SubscriptionProduct.builder()
                .name("Pro")
                .description("프로 플랜")
                .features(List.of("AI 면접 연습 주 10회", "개선 연습 주 4회", "AI 심층 분석 리포트 제공"))
                .planTier(PlanTier.PRO)
                .maxQuestions(7)
                .weeklyInterviewLimit(10)
                .weeklyImprovementPracticeLimit(4)
                .isActive(true)
                .build());

        proMonthlyPlan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .product(proProduct)
                .price(new BigDecimal("9900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());
    }

    @Test
    @DisplayName("POST /api/subscriptions - 성공: 구독 생성")
    void createSubscription_Success() throws Exception {
        PaymentMethod paymentMethod = paymentMethodRepository.save(
                PaymentMethod.create(testUser, "billing_key_create", "1234****5678", "VISA", true)
        );
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                basicMonthlyPlan.getId(),
                new BigDecimal("5900"),
                paymentMethod.getId()
        );

        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.planSnapshot.name").value("Basic"))
                .andDo(document("subscription-create",
                        resource(SubscriptionDocs.createSuccess())
                ));
    }

    @Test
    @DisplayName("POST /api/subscriptions - 실패: 무료 플랜 구독 시도")
    void createSubscription_FreePlan() throws Exception {
        PaymentMethod paymentMethod = paymentMethodRepository.save(
                PaymentMethod.create(testUser, "billing_key_free_err", "1234****5678", "VISA", true)
        );
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                freePlan.getId(),
                new BigDecimal("100"),
                paymentMethod.getId()
        );

        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andDo(document("subscription-create-free-plan",
                        resource(SubscriptionDocs.createFreePlanError())
                ));
    }

    @Test
    @DisplayName("POST /api/subscriptions - 실패: 존재하지 않는 플랜")
    void createSubscription_PlanNotFound() throws Exception {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                99999,
                new BigDecimal("5900"),
                1L
        );

        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("subscription-create-plan-not-found",
                        resource(SubscriptionDocs.createPlanNotFoundError())
                ));
    }

    @Test
    @DisplayName("GET /api/subscriptions - 성공: 내 구독 조회 (결제수단 정보 포함)")
    void getSubscription_Success() throws Exception {
        PaymentMethod paymentMethod = paymentMethodRepository.save(
                PaymentMethod.create(testUser, "billing_key_get", "1234****5678", "VISA", true)
        );
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan, paymentMethod);
        subscription.activate();
        subscription = subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/subscriptions")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(subscription.getId()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.cancelAtPeriodEnd").value(false))
                .andExpect(jsonPath("$.data.currentPeriodStart").exists())
                .andExpect(jsonPath("$.data.currentPeriodEnd").exists())
                .andExpect(jsonPath("$.data.nextBillingDate").exists())
                .andExpect(jsonPath("$.data.nextBillingAmount").value(5900))
                .andExpect(jsonPath("$.data.cardBrand").value("VISA"))
                .andExpect(jsonPath("$.data.cardNumber").value("1234****5678"))
                .andExpect(jsonPath("$.data.planSnapshot.name").value("Basic"))
                .andDo(document("subscription-get",
                        resource(SubscriptionDocs.getSuccess())
                ));
    }

    @Test
    @DisplayName("GET /api/subscriptions - 성공: 해지 예약(cancelAtPeriodEnd=true) 구독 조회 시 다음 결제 정보 null")
    void getSubscription_CancelAtPeriodEnd() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscription.scheduleCancelAtPeriodEnd();
        subscription = subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/subscriptions")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(subscription.getId()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.cancelAtPeriodEnd").value(true))
                .andExpect(jsonPath("$.data.nextBillingDate").doesNotExist())
                .andExpect(jsonPath("$.data.nextBillingAmount").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/subscriptions - 성공: PAST_DUE 구독 조회")
    void getSubscription_PastDue() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        Instant pastDueAt = Instant.parse("2026-08-25T03:00:00Z");
        subscription.markAsPastDue(pastDueAt);
        subscription = subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/subscriptions")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(subscription.getId()))
                .andExpect(jsonPath("$.data.status").value("PAST_DUE"))
                .andExpect(jsonPath("$.data.pastDueAt").value("2026-08-25T03:00:00Z"))
                .andExpect(jsonPath("$.data.gracePeriodEndsAt").value("2026-09-01T03:00:00Z"))
                .andExpect(jsonPath("$.data.nextBillingDate").doesNotExist())
                .andExpect(jsonPath("$.data.nextBillingAmount").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/subscriptions - 성공: UNPAID 구독 조회")
    void getSubscription_Unpaid() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscription.markAsPastDue(Instant.parse("2026-08-10T03:00:00Z"));
        subscription.markAsUnpaid();
        subscription = subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/subscriptions")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(subscription.getId()))
                .andExpect(jsonPath("$.data.status").value("UNPAID"))
                .andExpect(jsonPath("$.data.nextBillingDate").doesNotExist())
                .andExpect(jsonPath("$.data.nextBillingAmount").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/subscriptions - 실패: 현재 구독 없음")
    void getSubscription_NoSubscription() throws Exception {
        mockMvc.perform(get("/api/subscriptions")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("subscription-get-not-found",
                        resource(SubscriptionDocs.getNotFound())
                ));
    }

    @Test
    @DisplayName("GET /api/subscriptions - 실패: 결제 확정 전 PENDING 구독은 현재 구독으로 노출하지 않음")
    void getSubscription_PendingSubscription() throws Exception {
        subscriptionRepository.save(Subscription.create(testUser.getId(), basicMonthlyPlan));

        mockMvc.perform(get("/api/subscriptions")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /api/subscriptions/{id} - 성공: 기본 해지 요청 시 기간 만료 해지 예약(ACTIVE 유지)")
    void cancelSubscription_Success() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscription.setCurrentPaymentScheduleId("schedule_to_revoke");
        subscription = subscriptionRepository.save(subscription);

        CancelSubscriptionRequest request = new CancelSubscriptionRequest(false);

        mockMvc.perform(delete("/api/subscriptions/{id}", subscription.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.cancelAtPeriodEnd").value(true))
                .andExpect(jsonPath("$.data.canceledAt").exists())
                .andExpect(jsonPath("$.data.availableUntil").exists())
                .andDo(document("subscription-cancel",
                        resource(SubscriptionDocs.cancelSuccess())
                ));
    }

    @Test
    @DisplayName("DELETE /api/subscriptions/{id} - 성공: 바디 없이 호출해도 기간 만료 해지 예약으로 동작")
    void cancelSubscription_WithoutBody_DefaultsToPeriodEnd() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscription = subscriptionRepository.save(subscription);

        mockMvc.perform(delete("/api/subscriptions/{id}", subscription.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.cancelAtPeriodEnd").value(true));
    }

    @Test
    @DisplayName("DELETE /api/subscriptions/{id} - 성공: immediate=true 해지 시 즉시 CANCELED 후 Free 플랜 배정")
    void cancelSubscription_Immediate_AssignsFreePlan() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscription = subscriptionRepository.save(subscription);

        mockMvc.perform(delete("/api/subscriptions/{id}", subscription.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelSubscriptionRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.cancelAtPeriodEnd").value(false));

        Subscription current = subscriptionRepository.findCurrentByUserId(testUser.getId()).orElseThrow();
        assertThat(current.getSubscriptionPlan().getId()).isEqualTo(freePlan.getId());
        assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("DELETE /api/subscriptions/{id} - 실패: 타인의 구독 해지 시도")
    void cancelSubscription_Forbidden() throws Exception {
        User otherUser = User.builder()
                .email("other@example.com")
                .name("다른 사용자")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("other-google-id")
                .build();
        otherUser = userRepository.save(otherUser);

        Subscription otherSubscription = Subscription.create(otherUser.getId(), basicMonthlyPlan);
        otherSubscription.activate();
        otherSubscription = subscriptionRepository.save(otherSubscription);

        mockMvc.perform(delete("/api/subscriptions/{id}", otherSubscription.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andDo(document("subscription-cancel-forbidden",
                        resource(SubscriptionDocs.cancelForbiddenError())
                ));
    }

    @Test
    @DisplayName("PUT /api/subscriptions/{id}/payment-method - 성공: 결제 수단 변경")
    void updatePaymentMethod_Success() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscription.setCurrentPaymentScheduleId("test_schedule_id");
        subscription = subscriptionRepository.save(subscription);

        PaymentMethod oldPaymentMethod = PaymentMethod.create(
                testUser, "old_billing_key", "1234****5678", "VISA", true
        );
        paymentMethodRepository.save(oldPaymentMethod);

        PaymentMethod newPaymentMethod = PaymentMethod.create(
                testUser, "new_billing_key", "9876****4321", "MASTERCARD", false
        );
        newPaymentMethod = paymentMethodRepository.save(newPaymentMethod);

        UpdatePaymentMethodRequest request = new UpdatePaymentMethodRequest(newPaymentMethod.getId());

        mockMvc.perform(put("/api/subscriptions/{id}/payment-method", subscription.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andDo(document("subscription-payment-method-update",
                        resource(SubscriptionDocs.updatePaymentMethodSuccess())
                ));
    }

    @Test
    @DisplayName("PUT /api/subscriptions/{id}/payment-method - 성공: UNPAID 상태에서 결제 수단 교체 시 즉시 결제 후 ACTIVE 복구")
    void updatePaymentMethod_UnpaidRecovery_Success() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscription.markAsPastDue(Instant.parse("2026-08-01T00:00:00Z"));
        subscription.markAsUnpaid();
        subscription = subscriptionRepository.save(subscription);

        PaymentMethod newPaymentMethod = paymentMethodRepository.save(
                PaymentMethod.create(testUser, "recovery_billing_key", "5555****4444", "VISA", true)
        );
        UpdatePaymentMethodRequest request = new UpdatePaymentMethodRequest(newPaymentMethod.getId());

        mockMvc.perform(put("/api/subscriptions/{id}/payment-method", subscription.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Subscription recovered = subscriptionRepository.findById(subscription.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(recovered.getPastDueAt()).isNull();
    }

    @Test
    @DisplayName("PUT /api/subscriptions/{id}/payment-method - 실패: 구독을 찾을 수 없음")
    void updatePaymentMethod_SubscriptionNotFound() throws Exception {
        PaymentMethod newPaymentMethod = PaymentMethod.create(
                testUser, "new_billing_key", "9876****4321", "MASTERCARD", false
        );
        newPaymentMethod = paymentMethodRepository.save(newPaymentMethod);

        UpdatePaymentMethodRequest request = new UpdatePaymentMethodRequest(newPaymentMethod.getId());

        mockMvc.perform(put("/api/subscriptions/{id}/payment-method", 99999L)
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("subscription-payment-method-update-not-found",
                        resource(SubscriptionDocs.updatePaymentMethodNotFoundError())
                ));
    }

    @Test
    @DisplayName("PUT /api/subscriptions/{id}/payment-method - 실패: 타인의 구독")
    void updatePaymentMethod_Forbidden() throws Exception {
        User otherUser = User.builder()
                .email("other@example.com")
                .name("다른 사용자")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("other-google-id")
                .build();
        otherUser = userRepository.save(otherUser);

        Subscription otherSubscription = Subscription.create(otherUser.getId(), basicMonthlyPlan);
        otherSubscription.activate();
        otherSubscription = subscriptionRepository.save(otherSubscription);

        PaymentMethod myPaymentMethod = PaymentMethod.create(
                testUser, "my_billing_key", "1234****5678", "VISA", true
        );
        myPaymentMethod = paymentMethodRepository.save(myPaymentMethod);

        UpdatePaymentMethodRequest request = new UpdatePaymentMethodRequest(myPaymentMethod.getId());

        mockMvc.perform(put("/api/subscriptions/{id}/payment-method", otherSubscription.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andDo(document("subscription-payment-method-update-forbidden",
                        resource(SubscriptionDocs.updatePaymentMethodForbiddenError())
                ));
    }

    @Test
    @DisplayName("PUT /api/subscriptions/{id}/payment-method - 실패: 결제 수단을 찾을 수 없음")
    void updatePaymentMethod_PaymentMethodNotFound() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscription = subscriptionRepository.save(subscription);

        UpdatePaymentMethodRequest request = new UpdatePaymentMethodRequest(99999L);

        mockMvc.perform(put("/api/subscriptions/{id}/payment-method", subscription.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /api/subscriptions/{id}/payment-method - 실패: 비활성화된 결제 수단")
    void updatePaymentMethod_InactivePaymentMethod() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscription = subscriptionRepository.save(subscription);

        PaymentMethod inactivePaymentMethod = PaymentMethod.create(
                testUser, "inactive_billing_key", "1234****5678", "VISA", false
        );
        inactivePaymentMethod.deactivate();
        inactivePaymentMethod = paymentMethodRepository.save(inactivePaymentMethod);

        UpdatePaymentMethodRequest request = new UpdatePaymentMethodRequest(inactivePaymentMethod.getId());

        mockMvc.perform(put("/api/subscriptions/{id}/payment-method", subscription.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("GET /api/subscriptions/plan-change - 성공: 업그레이드 예상 금액 조회 (targetPlanId 및 targetPlan 별칭 지원)")
    void previewPlanChange_Upgrade_Success() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/subscriptions/plan-change")
                        .param("targetPlanId", String.valueOf(proMonthlyPlan.getId()))
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentPlan.name").value("Basic"))
                .andExpect(jsonPath("$.data.targetPlan.name").value("Pro"))
                .andExpect(jsonPath("$.data.immediateAmount").value(4000))
                .andExpect(jsonPath("$.data.nextBillingAmount").value(9900))
                .andExpect(jsonPath("$.data.nextBillingDate").exists())
                .andDo(document("subscription-plan-change-preview",
                        resource(SubscriptionDocs.planChangePreviewSuccess())
                ));

        // 하위 호환 targetPlan 별칭 동작 확인
        mockMvc.perform(get("/api/subscriptions/plan-change")
                        .param("targetPlan", String.valueOf(proMonthlyPlan.getId()))
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.immediateAmount").value(4000));
    }

    @Test
    @DisplayName("GET /api/subscriptions/plan-change - 실패: 동일 플랜으로 변경 요청")
    void previewPlanChange_SamePlan_BadRequest() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/subscriptions/plan-change")
                        .param("targetPlanId", String.valueOf(basicMonthlyPlan.getId()))
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andDo(document("subscription-plan-change-preview-bad-request",
                        resource(SubscriptionDocs.planChangePreviewBadRequestError())
                ));
    }

    @Test
    @DisplayName("GET /api/subscriptions/plan-change - 실패: 대상 플랜 없음")
    void previewPlanChange_TargetNotFound() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/subscriptions/plan-change")
                        .param("targetPlanId", "99999")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("subscription-plan-change-preview-not-found",
                        resource(SubscriptionDocs.planChangePreviewNotFoundError())
                ));
    }

    @Test
    @DisplayName("POST /api/subscriptions/plan-change - 성공: Basic → Pro 즉시 업그레이드")
    void changePlan_Upgrade_Success() throws Exception {
        PaymentMethod paymentMethod = paymentMethodRepository.save(
                PaymentMethod.create(testUser, "upgrade_billing_key", "1234****5678", "VISA", true)
        );
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan, paymentMethod);
        subscription.activate();
        subscriptionRepository.save(subscription);

        CreateSubscriptionPlanChangeRequest request = new CreateSubscriptionPlanChangeRequest(
                proMonthlyPlan.getId(),
                new BigDecimal("4000")
        );

        mockMvc.perform(post("/api/subscriptions/plan-change")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chargedAmount").value(4000))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.subscription.planSnapshot.name").value("Pro"))
                .andExpect(jsonPath("$.data.subscription.nextBillingAmount").value(9900))
                .andDo(document("subscription-plan-change",
                        resource(SubscriptionDocs.planChangeSuccess())
                ));
    }

    @Test
    @DisplayName("POST /api/subscriptions/plan-change - 성공: Pro → Basic 다운그레이드 예약")
    void changePlan_Downgrade_Success() throws Exception {
        PaymentMethod paymentMethod = paymentMethodRepository.save(
                PaymentMethod.create(testUser, "downgrade_billing_key", "1234****5678", "VISA", true)
        );
        Subscription subscription = Subscription.create(testUser.getId(), proMonthlyPlan, paymentMethod);
        subscription.activate();
        subscriptionRepository.save(subscription);

        CreateSubscriptionPlanChangeRequest request = new CreateSubscriptionPlanChangeRequest(
                basicMonthlyPlan.getId(),
                BigDecimal.ZERO
        );

        mockMvc.perform(post("/api/subscriptions/plan-change")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chargedAmount").value(0))
                .andExpect(jsonPath("$.data.subscription.planSnapshot.name").value("Pro"))
                .andExpect(jsonPath("$.data.subscription.scheduledPlanName").value("Basic"))
                .andExpect(jsonPath("$.data.subscription.nextBillingAmount").value(5900));
    }

    @Test
    @DisplayName("POST /api/subscriptions/plan-change - 실패: 확인 금액 불일치")
    void changePlan_ConfirmedAmountMismatch_BadRequest() throws Exception {
        PaymentMethod paymentMethod = paymentMethodRepository.save(
                PaymentMethod.create(testUser, "mismatch_billing_key", "1234****5678", "VISA", true)
        );
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan, paymentMethod);
        subscription.activate();
        subscriptionRepository.save(subscription);

        CreateSubscriptionPlanChangeRequest request = new CreateSubscriptionPlanChangeRequest(
                proMonthlyPlan.getId(),
                new BigDecimal("1000")
        );

        mockMvc.perform(post("/api/subscriptions/plan-change")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andDo(document("subscription-plan-change-bad-request",
                        resource(SubscriptionDocs.planChangeBadRequestError())
                ));
    }

    @Test
    @DisplayName("POST /api/subscriptions/plan-change - 실패: 대상 플랜 없음")
    void changePlan_TargetPlanNotFound() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscriptionRepository.save(subscription);

        CreateSubscriptionPlanChangeRequest request = new CreateSubscriptionPlanChangeRequest(
                99999,
                BigDecimal.ZERO
        );

        mockMvc.perform(post("/api/subscriptions/plan-change")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("subscription-plan-change-not-found",
                        resource(SubscriptionDocs.planChangeNotFoundError())
                ));
    }
}
