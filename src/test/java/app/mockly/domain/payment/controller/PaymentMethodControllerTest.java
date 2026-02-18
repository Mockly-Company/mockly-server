package app.mockly.domain.payment.controller;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.controller.docs.AddPaymentMethodDocs;
import app.mockly.domain.payment.controller.docs.DeletePaymentMethodDocs;
import app.mockly.domain.payment.controller.docs.GetPaymentMethodsDocs;
import app.mockly.domain.payment.controller.docs.SetDefaultPaymentMethodDocs;
import app.mockly.domain.payment.dto.request.AddPaymentMethodRequest;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
import app.mockly.domain.product.entity.*;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.portone.sdk.server.common.Card;
import io.portone.sdk.server.common.CardBrand;
import io.portone.sdk.server.payment.billingkey.BillingKeyInfo;
import io.portone.sdk.server.payment.billingkey.BillingKeyPaymentMethodCard;
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

import java.util.List;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class PaymentMethodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionProductRepository subscriptionProductRepository;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PortOneService portOneService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    private User testUser;
    private String validAccessToken;
    private SubscriptionPlan testPlan;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .name("테스트 사용자")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("test-google-id")
                .build();
        testUser = userRepository.save(testUser);

        validAccessToken = jwtService.generateAccessToken(testUser.getId());

        // TokenBlacklistService 모킹 - 모든 토큰을 블랙리스트에 없다고 응답
        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);

        // 테스트용 구독 플랜 생성
        SubscriptionProduct product = SubscriptionProduct.builder()
                .name("테스트 상품")
                .description("테스트용 구독 상품")
                .build();
        subscriptionProductRepository.save(product);

        testPlan = SubscriptionPlan.builder()
                .product(product)
                .price(new java.math.BigDecimal("9900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build();
        subscriptionPlanRepository.save(testPlan);
    }

    @Test
    @DisplayName("결제 수단 추가 - 성공 (첫 번째 결제 수단)")
    void addPaymentMethod_First_Success() throws Exception {
        // Given
        String billingKey = "billing_key_12345";
        AddPaymentMethodRequest request = new AddPaymentMethodRequest(billingKey);

        // PortOne BillingKeyInfo Mock
        BillingKeyInfo mockBillingKeyInfo = createMockBillingKeyInfo("1234****5678****9012", "VISA");
        given(portOneService.getBillingKey(anyString())).willReturn(mockBillingKeyInfo);

        // When & Then
        mockMvc.perform(post("/api/payment-methods")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.cardBrand").value("VISA"))
                .andExpect(jsonPath("$.data.cardNumber").value("1234****5678****9012"))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andDo(document("add-payment-method",
                        resource(AddPaymentMethodDocs.success())));
    }

    @Test
    @DisplayName("결제 수단 추가 - 성공 (두 번째 결제 수단)")
    void addPaymentMethod_Second_Success() throws Exception {
        // Given - 첫 번째 결제 수단 미리 생성
        PaymentMethod firstPaymentMethod = PaymentMethod.create(
                testUser,
                "existing_billing_key",
                "9999****8888****7777",
                "MASTERCARD",
                true
        );
        paymentMethodRepository.save(firstPaymentMethod);

        String billingKey = "billing_key_67890";
        AddPaymentMethodRequest request = new AddPaymentMethodRequest(billingKey);

        // PortOne BillingKeyInfo Mock
        BillingKeyInfo mockBillingKeyInfo = createMockBillingKeyInfo("9876****4321****1111", "MASTERCARD");
        given(portOneService.getBillingKey(anyString())).willReturn(mockBillingKeyInfo);

        // When & Then
        mockMvc.perform(post("/api/payment-methods")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isDefault").value(false));
    }

    @Test
    @DisplayName("결제 수단 목록 조회 - 성공 (빈 목록)")
    void getPaymentMethods_Empty_Success() throws Exception {
        // Given - 결제 수단 없음

        // When & Then
        mockMvc.perform(get("/api/payment-methods")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andDo(document("get-payment-methods",
                        resource(GetPaymentMethodsDocs.success())));
    }

    @Test
    @DisplayName("결제 수단 목록 조회 - 성공 (단일 결제 수단)")
    void getPaymentMethods_Single_Success() throws Exception {
        // Given
        PaymentMethod paymentMethod = PaymentMethod.create(
                testUser,
                "billing_key_123",
                "1234****5678****9012",
                "VISA",
                true
        );
        paymentMethodRepository.save(paymentMethod);

        // When & Then
        mockMvc.perform(get("/api/payment-methods")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].cardBrand").value("VISA"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    @DisplayName("결제 수단 목록 조회 - 성공 (복수 결제 수단)")
    void getPaymentMethods_Multiple_Success() throws Exception {
        // Given
        PaymentMethod pm1 = PaymentMethod.create(
                testUser,
                "billing_key_1",
                "1111****2222****3333",
                "VISA",
                true
        );
        PaymentMethod pm2 = PaymentMethod.create(
                testUser,
                "billing_key_2",
                "4444****5555****6666",
                "MASTERCARD",
                false
        );
        paymentMethodRepository.save(pm1);
        paymentMethodRepository.save(pm2);

        // When & Then
        mockMvc.perform(get("/api/payment-methods")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("결제 수단 삭제 - 성공")
    void deletePaymentMethod_Success() throws Exception {
        // Given - 두 개의 결제 수단 생성 (첫 번째는 기본, 두 번째는 일반)
        PaymentMethod defaultPaymentMethod = PaymentMethod.create(
                testUser,
                "billing_key_default",
                "1111****2222****3333",
                "VISA",
                true
        );
        PaymentMethod normalPaymentMethod = PaymentMethod.create(
                testUser,
                "billing_key_normal",
                "4444****5555****6666",
                "MASTERCARD",
                false
        );
        paymentMethodRepository.save(defaultPaymentMethod);
        PaymentMethod saved = paymentMethodRepository.save(normalPaymentMethod);

        // When & Then - 일반 결제 수단 삭제
        mockMvc.perform(delete("/api/payment-methods/{paymentMethodId}", saved.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty())
                .andDo(document("delete-payment-method",
                        resource(DeletePaymentMethodDocs.success())));
    }

    @Test
    @DisplayName("결제 수단 삭제 - 실패 (활성 구독 + 기본 결제 수단)")
    void deletePaymentMethod_ActiveSubscriptionWithDefaultFail() throws Exception {
        // Given - 기본 결제 수단 생성
        PaymentMethod defaultPaymentMethod = PaymentMethod.create(
                testUser,
                "billing_key_default",
                "1111****2222****3333",
                "VISA",
                true
        );
        PaymentMethod saved = paymentMethodRepository.save(defaultPaymentMethod);

        // Given - 활성 구독 생성
        Subscription subscription = Subscription.create(testUser.getId(), testPlan);
        subscription.activate();
        subscriptionRepository.save(subscription);

        // When & Then
        mockMvc.perform(delete("/api/payment-methods/{paymentMethodId}", saved.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("활성 구독에 사용 중인 기본 결제 수단입니다. 구독을 해지하거나 다른 결제 수단을 기본으로 설정 후 삭제해주세요."));
    }

    @Test
    @DisplayName("결제 수단 삭제 - 성공 (활성 구독 + 일반 결제 수단)")
    void deletePaymentMethod_ActiveSubscriptionWithNonDefaultSuccess() throws Exception {
        // Given - 기본 결제 수단과 일반 결제 수단 생성
        PaymentMethod defaultPaymentMethod = PaymentMethod.create(
                testUser,
                "billing_key_default",
                "1111****2222****3333",
                "VISA",
                true
        );
        PaymentMethod normalPaymentMethod = PaymentMethod.create(
                testUser,
                "billing_key_normal",
                "4444****5555****6666",
                "MASTERCARD",
                false
        );
        paymentMethodRepository.save(defaultPaymentMethod);
        PaymentMethod saved = paymentMethodRepository.save(normalPaymentMethod);

        // Given - 활성 구독 생성
        Subscription subscription = Subscription.create(testUser.getId(), testPlan);
        subscription.activate();
        subscriptionRepository.save(subscription);

        // When & Then - 일반 결제 수단은 삭제 가능
        mockMvc.perform(delete("/api/payment-methods/{paymentMethodId}", saved.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("결제 수단 삭제 - 성공 (구독 없음 + 기본 결제 수단)")
    void deletePaymentMethod_NoSubscriptionWithDefaultSuccess() throws Exception {
        // Given - 기본 결제 수단만 생성 (활성 구독 없음)
        PaymentMethod defaultPaymentMethod = PaymentMethod.create(
                testUser,
                "billing_key_default",
                "1111****2222****3333",
                "VISA",
                true
        );
        PaymentMethod saved = paymentMethodRepository.save(defaultPaymentMethod);

        // When & Then - 활성 구독이 없으면 기본 결제 수단도 삭제 가능
        mockMvc.perform(delete("/api/payment-methods/{paymentMethodId}", saved.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("결제 수단 삭제 - 실패 (존재하지 않는 결제 수단)")
    void deletePaymentMethod_NotFoundFail() throws Exception {
        // Given - 존재하지 않는 ID
        Long nonExistentId = 99999L;

        // When & Then
        mockMvc.perform(delete("/api/payment-methods/{paymentMethodId}", nonExistentId)
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("결제 수단을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("기본 결제 수단 변경 - 성공")
    void setDefaultPaymentMethod_Success() throws Exception {
        // Given - 두 개의 결제 수단 생성 (첫 번째는 기본, 두 번째는 일반)
        PaymentMethod defaultPaymentMethod = PaymentMethod.create(
                testUser,
                "billing_key_default",
                "1111****2222****3333",
                "VISA",
                true
        );
        PaymentMethod normalPaymentMethod = PaymentMethod.create(
                testUser,
                "billing_key_normal",
                "4444****5555****6666",
                "MASTERCARD",
                false
        );
        paymentMethodRepository.save(defaultPaymentMethod);
        PaymentMethod saved = paymentMethodRepository.save(normalPaymentMethod);

        // When & Then - 일반 결제 수단을 기본으로 변경
        mockMvc.perform(patch("/api/payment-methods/{paymentMethodId}/default", saved.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(saved.getId()))
                .andExpect(jsonPath("$.data.cardBrand").value("MASTERCARD"))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andDo(document("set-default-payment-method",
                        resource(SetDefaultPaymentMethodDocs.success())));
    }

    @Test
    @DisplayName("기본 결제 수단 변경 - 성공 (이미 기본인 경우, idempotent)")
    void setDefaultPaymentMethod_AlreadyDefaultSuccess() throws Exception {
        // Given - 기본 결제 수단 생성
        PaymentMethod defaultPaymentMethod = PaymentMethod.create(
                testUser,
                "billing_key_default",
                "1111****2222****3333",
                "VISA",
                true
        );
        PaymentMethod saved = paymentMethodRepository.save(defaultPaymentMethod);

        // When & Then - 이미 기본인 결제 수단을 다시 기본으로 설정
        mockMvc.perform(patch("/api/payment-methods/{paymentMethodId}/default", saved.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(saved.getId()))
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    @DisplayName("기본 결제 수단 변경 - 실패 (존재하지 않는 결제 수단)")
    void setDefaultPaymentMethod_NotFoundFail() throws Exception {
        // Given - 존재하지 않는 ID
        Long nonExistentId = 99999L;

        // When & Then
        mockMvc.perform(patch("/api/payment-methods/{paymentMethodId}/default", nonExistentId)
                        .header("Authorization", "Bearer " + validAccessToken))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("결제 수단을 찾을 수 없습니다."));
    }

    private BillingKeyInfo createMockBillingKeyInfo(String cardNumber, String cardBrand) {
        Card mockCard = mock(Card.class);
        given(mockCard.getNumber()).willReturn(cardNumber);
        given(mockCard.getBrand()).willReturn(mock(CardBrand.class));
        given(mockCard.getBrand().toString()).willReturn(cardBrand);

        BillingKeyPaymentMethodCard mockCardMethod = mock(BillingKeyPaymentMethodCard.class);
        given(mockCardMethod.getCard()).willReturn(mockCard);

        BillingKeyInfo.Recognized mockRecognized = mock(BillingKeyInfo.Recognized.class);
        given(mockRecognized.getMethods()).willReturn(List.of(mockCardMethod));

        return mockRecognized;
    }
}
