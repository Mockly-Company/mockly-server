package app.mockly.domain.product.controller;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.product.entity.*;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.domain.product.controller.docs.SubscriptionProductDocs;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class SubscriptionProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

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
    private SubscriptionPlan freeMonthlyPlan;
    private SubscriptionPlan basicMonthlyPlan;
    private SubscriptionPlan proMonthlyPlan;

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

        // 조회 정렬이 저장 순서에 의존하지 않는지 확인하기 위해 PRO부터 저장한다.
        SubscriptionProduct proProduct = createProduct(
                "Pro", "프로 플랜", PlanTier.PRO, 10, 10, 4, true
        );
        proMonthlyPlan = createPlan(proProduct, "9900", Currency.KRW, BillingCycle.MONTHLY);
        createPlan(proProduct, "9", Currency.USD, BillingCycle.MONTHLY);

        SubscriptionProduct freeProduct = createProduct(
                "Free", "무료 플랜", PlanTier.FREE, 3, 1, 0, true
        );
        freeMonthlyPlan = createPlan(freeProduct, "0", Currency.KRW, BillingCycle.MONTHLY);
        createPlan(freeProduct, "0", Currency.KRW, BillingCycle.LIFETIME);

        SubscriptionProduct basicProduct = createProduct(
                "Basic", "베이직 플랜", PlanTier.BASIC, 5, 4, 0, true
        );
        basicMonthlyPlan = createPlan(basicProduct, "5900", Currency.KRW, BillingCycle.MONTHLY);
        createPlan(basicProduct, "59000", Currency.KRW, BillingCycle.YEARLY);

        SubscriptionProduct inactiveProduct = createProduct(
                "Inactive", "비활성 상품", PlanTier.BASIC, 5, 4, 0, false
        );
        createPlan(inactiveProduct, "4900", Currency.KRW, BillingCycle.MONTHLY);
    }

    private SubscriptionProduct createProduct(
            String name,
            String description,
            PlanTier planTier,
            int maxQuestions,
            int weeklyInterviewLimit,
            int weeklyImprovementPracticeLimit,
            boolean isActive
    ) {
        return subscriptionProductRepository.save(SubscriptionProduct.builder()
                .name(name)
                .description(description)
                .planTier(planTier)
                .maxQuestions(maxQuestions)
                .weeklyInterviewLimit(weeklyInterviewLimit)
                .weeklyImprovementPracticeLimit(weeklyImprovementPracticeLimit)
                .features(List.of("기본 기능"))
                .isActive(isActive)
                .build());
    }

    private SubscriptionPlan createPlan(
            SubscriptionProduct product,
            String price,
            Currency currency,
            BillingCycle billingCycle
    ) {
        SubscriptionPlan plan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .product(product)
                .price(new BigDecimal(price))
                .currency(currency)
                .billingCycle(billingCycle)
                .build());
        product.addPlan(plan);
        return plan;
    }

    @Test
    @DisplayName("GET /api/subscription-products - 비로그인: 상품 목록 조회")
    void getProducts_NoAuth() throws Exception {
        mockMvc.perform(get("/api/subscription-products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.products.length()").value(3))
                .andExpect(jsonPath("$.data.products[0].planTier").value("FREE"))
                .andExpect(jsonPath("$.data.products[0].maxQuestions").value(3))
                .andExpect(jsonPath("$.data.products[0].weeklyInterviewLimit").value(1))
                .andExpect(jsonPath("$.data.products[0].weeklyImprovementPracticeLimit").value(0))
                .andExpect(jsonPath("$.data.products[1].planTier").value("BASIC"))
                .andExpect(jsonPath("$.data.products[2].planTier").value("PRO"))
                .andExpect(jsonPath("$.data.products[2].weeklyImprovementPracticeLimit").value(4))
                .andExpect(jsonPath("$.data.products[*].plans.length()", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(1))))
                .andExpect(jsonPath("$.data.products[*].plans[*].billingCycle", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("MONTHLY"))))
                .andExpect(jsonPath("$.data.products[*].plans[*].currency", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("KRW"))))
                .andExpect(jsonPath("$.data.products[0].plans[0].isActive").value(false))
                .andExpect(jsonPath("$.data.products[1].plans[0].isActive").value(false))
                .andExpect(jsonPath("$.data.products[2].plans[0].isActive").value(false))
                .andDo(document("subscription-products-no-auth",
                        resource(SubscriptionProductDocs.getProductsNoAuth())
                ));
    }

    @Test
    @DisplayName("GET /api/subscription-products - 로그인 (구독 없음): 상품 목록 조회")
    void getProducts_AuthNoSubscription() throws Exception {
        mockMvc.perform(get("/api/subscription-products")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.products[0].plans[0].isActive").value(false))
                .andDo(document("subscription-products-auth-no-sub",
                        resource(SubscriptionProductDocs.getProductsAuthNoSubscription())
                ));
    }

    @ParameterizedTest(name = "{0} ACTIVE 구독")
    @CsvSource({"FREE,0", "BASIC,1", "PRO,2"})
    @DisplayName("GET /api/subscription-products - ACTIVE 구독의 플랜 하나만 활성 표시")
    void getProducts_AuthWithSubscription(PlanTier planTier, int activeProductIndex) throws Exception {
        SubscriptionPlan currentPlan = switch (planTier) {
            case FREE -> freeMonthlyPlan;
            case BASIC -> basicMonthlyPlan;
            case PRO -> proMonthlyPlan;
        };
        Subscription subscription = Subscription.create(testUser.getId(), currentPlan);
        subscription.activate();
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/subscription-products")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.products[" + activeProductIndex + "].plans[0].isActive").value(true))
                .andExpect(jsonPath("$.data.products[?(@.plans[0].isActive == true)]", org.hamcrest.Matchers.hasSize(1)))
                .andDo(document("subscription-products-auth-with-sub",
                        resource(SubscriptionProductDocs.getProductsAuthWithSubscription())
                ));
    }

    @Test
    @DisplayName("GET /api/subscription-products - PAST_DUE 구독도 현재 플랜으로 표시")
    void getProducts_PastDueSubscription() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), proMonthlyPlan);
        subscription.activate();
        subscription.markAsPastDue();
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/subscription-products")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products[2].plans[0].isActive").value(true))
                .andExpect(jsonPath("$.data.products[?(@.plans[0].isActive == true)]", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/subscription-products - UNPAID 구독은 활성 플랜으로 표시하지 않음")
    void getProducts_UnpaidSubscription() throws Exception {
        Subscription subscription = Subscription.create(testUser.getId(), proMonthlyPlan);
        subscription.activate();
        subscription.markAsPastDue(Instant.parse("2026-08-01T00:00:00Z"));
        subscription.markAsUnpaid();
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/subscription-products")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products[?(@.plans[0].isActive == true)]", org.hamcrest.Matchers.hasSize(0)));
    }

}
