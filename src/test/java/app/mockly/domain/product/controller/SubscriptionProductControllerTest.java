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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
                .features(List.of("기본 기능"))
                .isActive(true)
                .build();
        freeProduct = subscriptionProductRepository.save(freeProduct);

        SubscriptionPlan freePlan = SubscriptionPlan.builder()
                .product(freeProduct)
                .price(BigDecimal.ZERO)
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.LIFETIME)
                .build();
        freePlan = subscriptionPlanRepository.save(freePlan);
        freeProduct.addPlan(freePlan);

        SubscriptionProduct basicProduct = SubscriptionProduct.builder()
                .name("Basic")
                .description("베이직 플랜")
                .features(List.of("기본 기능", "추가 기능"))
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
        basicProduct.addPlan(basicMonthlyPlan);

        SubscriptionPlan basicYearlyPlan = SubscriptionPlan.builder()
                .product(basicProduct)
                .price(new BigDecimal("99000"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.YEARLY)
                .build();
        basicYearlyPlan = subscriptionPlanRepository.save(basicYearlyPlan);
        basicProduct.addPlan(basicYearlyPlan);
    }

    @Test
    @DisplayName("GET /api/subscription-products - 비로그인: 상품 목록 조회")
    void getProducts_NoAuth() throws Exception {
        mockMvc.perform(get("/api/subscription-products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.products[0].name").exists())
                .andExpect(jsonPath("$.data.products[0].plans").isArray())
                .andExpect(jsonPath("$.data.products[0].plans[0].isActive").value(false))
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

    @Test
    @DisplayName("GET /api/subscription-products - 로그인 (구독 있음): 상품 목록 조회")
    void getProducts_AuthWithSubscription() throws Exception {
        // 구독 생성
        Subscription subscription = Subscription.create(testUser.getId(), basicMonthlyPlan);
        subscription.activate();
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/api/subscription-products")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.products").isArray())
                .andDo(document("subscription-products-auth-with-sub",
                        resource(SubscriptionProductDocs.getProductsAuthWithSubscription())
                ));
    }
}
