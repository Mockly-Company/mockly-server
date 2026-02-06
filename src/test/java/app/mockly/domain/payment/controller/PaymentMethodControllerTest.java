package app.mockly.domain.payment.controller;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.controller.docs.AddPaymentMethodDocs;
import app.mockly.domain.payment.controller.docs.GetPaymentMethodsDocs;
import app.mockly.domain.payment.dto.request.AddPaymentMethodRequest;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
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
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
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
    private JwtService jwtService;

    @MockitoBean
    private PortOneService portOneService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    private User testUser;
    private String validAccessToken;

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
