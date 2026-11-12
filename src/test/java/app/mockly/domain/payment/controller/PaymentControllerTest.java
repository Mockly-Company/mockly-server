package app.mockly.domain.payment.controller;

import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.payment.controller.docs.PaymentDocs;
import app.mockly.domain.payment.entity.Invoice;
import app.mockly.domain.payment.entity.Payment;
import app.mockly.domain.payment.entity.PaymentMethodType;
import app.mockly.domain.payment.repository.InvoiceRepository;
import app.mockly.domain.payment.repository.PaymentRepository;
import app.mockly.domain.product.entity.*;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionProductRepository productRepository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private String validAccessToken;
    private Payment paidPayment;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("payment-test@example.com")
                .name("결제 테스트 유저")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("payment-test-provider")
                .build());
        validAccessToken = jwtService.generateAccessToken(user.getId());
        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);

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

        Subscription subscription = Subscription.create(user.getId(), basicPlan);
        subscription.activate();
        subscription = subscriptionRepository.save(subscription);

        LocalDateTime now = LocalDateTime.now();
        Invoice invoice = Invoice.create(subscription, new BigDecimal("5900"), Currency.KRW, now, now.plusMonths(1));
        invoice.markAsPaid();
        invoice = invoiceRepository.save(invoice);

        paidPayment = Payment.create(invoice, new BigDecimal("5900"), Currency.KRW);
        paidPayment.markAsPaid(PaymentMethodType.CARD);
        paidPayment = paymentRepository.save(paidPayment);
    }

    @Test
    @DisplayName("GET /api/payments - 성공: 결제 내역 목록 조회")
    void getPayments_Success() throws Exception {
        mockMvc.perform(get("/api/payments")
                        .param("page", "1")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.payments[0].id").value(paidPayment.getId()))
                .andExpect(jsonPath("$.data.payments[0].status").value("PAID"))
                .andDo(document("payment-list",
                        resource(PaymentDocs.listSuccess())
                ));
    }

    @Test
    @DisplayName("GET /api/payments/{paymentId} - 성공: 결제 상세 조회")
    void getPaymentDetail_Success() throws Exception {
        mockMvc.perform(get("/api/payments/{paymentId}", paidPayment.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(paidPayment.getId()))
                .andExpect(jsonPath("$.data.product.name").value("Basic"))
                .andDo(document("payment-detail",
                        resource(PaymentDocs.detailSuccess())
                ));
    }

    @Test
    @DisplayName("GET /api/payments/{paymentId} - 실패: 존재하지 않는 결제 ID")
    void getPaymentDetail_NotFound() throws Exception {
        mockMvc.perform(get("/api/payments/{paymentId}", "pay_not_found")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("payment-detail-not-found",
                        resource(PaymentDocs.detailNotFoundError())
                ));
    }

    @Test
    @DisplayName("GET /api/payments/{paymentId}/invoice - 성공: 청구서 상세 조회")
    void getInvoiceDetail_Success() throws Exception {
        mockMvc.perform(get("/api/payments/{paymentId}/invoice", paidPayment.getId())
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andDo(document("payment-invoice-detail",
                        resource(PaymentDocs.invoiceSuccess())
                ));
    }

    @Test
    @DisplayName("GET /api/payments/{paymentId}/invoice - 실패: 존재하지 않는 결제 ID")
    void getInvoiceDetail_NotFound() throws Exception {
        mockMvc.perform(get("/api/payments/{paymentId}/invoice", "pay_not_found")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andDo(document("payment-invoice-detail-not-found",
                        resource(PaymentDocs.invoiceNotFoundError())
                ));
    }
}
