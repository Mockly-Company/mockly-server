package app.mockly.domain.product.controller;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.product.dto.request.CreateSubscriptionRequest;
import app.mockly.domain.product.entity.*;
import io.portone.sdk.server.payment.PayWithBillingKeyResponse;
import io.portone.sdk.server.payment.billingkey.BillingKeyInfo;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.domain.product.controller.docs.SubscriptionDocs;
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

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
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

    private User testUser;
    private String validAccessToken;
    private SubscriptionProduct basicProduct;
    private SubscriptionPlan freePlan;
    private SubscriptionPlan basicMonthlyPlan;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser = User.builder()
                .email("test@example.com")
                .name("테스트 사용자")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("test-google-provider-id")
                .build();
        testUser = userRepository.save(testUser);

        validAccessToken = jwtService.generateAccessToken(testUser.getId());

        // 토큰 블랙리스트 모킹
        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);

        // 테스트 상품 및 플랜 생성
        SubscriptionProduct freeProduct = SubscriptionProduct.builder()
                .name("Free")
                .description("무료 플랜")
                .isActive(true)
                .build();
        freeProduct = subscriptionProductRepository.save(freeProduct);

        freePlan = SubscriptionPlan.builder()
                .product(freeProduct)
                .price(BigDecimal.ZERO)
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.LIFETIME)
                .build();
        freePlan = subscriptionPlanRepository.save(freePlan);

        basicProduct = SubscriptionProduct.builder()
                .name("Basic")
                .description("베이직 플랜")
                .isActive(true)
                .build();
        basicProduct = subscriptionProductRepository.save(basicProduct);

        basicMonthlyPlan = SubscriptionPlan.builder()
                .product(basicProduct)
                .price(new BigDecimal("9900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build();
        basicMonthlyPlan = subscriptionPlanRepository.save(basicMonthlyPlan);
    }

    @Test
    @org.junit.jupiter.api.Disabled("PortOne Mock 응답 설정 필요")
    @DisplayName("POST /api/subscriptions - 성공: 구독 생성")
    void createSubscription_Success() throws Exception {
        // TODO: PortOne Mock 응답 설정 필요 - 실제 결제 연동 테스트는 별도로
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                basicMonthlyPlan.getId(),
                new BigDecimal("9900"),
                "billing_key_test_mock"
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
    @org.junit.jupiter.api.Disabled("PortOne Mock 응답 설정 필요")
    @DisplayName("POST /api/subscriptions - 실패: 무료 플랜 구독 시도")
    void createSubscription_FreePlan() throws Exception {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                freePlan.getId(),
                BigDecimal.ZERO,
                "billing_key_dummy"
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
                new BigDecimal("9900"),
                "billing_key_dummy"
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
    @DisplayName("GET /api/subscriptions - 성공: 내 구독 조회")
    void getSubscription_Success() throws Exception {
        // 구독 생성
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/subscriptions")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.planSnapshot.name").value("Basic"))
                .andDo(document("subscription-get",
                        resource(SubscriptionDocs.getSuccess())
                ));
    }

    @Test
    @DisplayName("GET /api/subscriptions - 성공: 구독 없음 (null 반환)")
    void getSubscription_NoSubscription() throws Exception {
        mockMvc.perform(get("/api/subscriptions")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andDo(document("subscription-get-empty",
                        resource(SubscriptionDocs.getEmpty())
                ));
    }

    @Test
    @DisplayName("DELETE /api/subscriptions/{id} - 성공: 구독 해지")
    void cancelSubscription_Success() throws Exception {
        // 구독 생성
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscription = subscriptionRepository.save(subscription);

        mockMvc.perform(delete("/api/subscriptions/{id}", subscription.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.canceledAt").exists())
                .andDo(document("subscription-cancel",
                        resource(SubscriptionDocs.cancelSuccess())
                ));
    }

    @Test
    @DisplayName("DELETE /api/subscriptions/{id} - 실패: 타인의 구독 해지 시도")
    void cancelSubscription_Forbidden() throws Exception {
        // 다른 사용자 생성
        User otherUser = User.builder()
                .email("other@example.com")
                .name("다른 사용자")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("other-google-id")
                .build();
        otherUser = userRepository.save(otherUser);

        // 다른 사용자의 구독 생성
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
}
