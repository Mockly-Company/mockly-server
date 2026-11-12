package app.mockly.domain.user.controller;

import app.mockly.domain.auth.dto.DeviceInfo;
import app.mockly.domain.auth.dto.LocationInfo;
import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.RefreshToken;
import app.mockly.domain.auth.entity.Session;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.RefreshTokenRepository;
import app.mockly.domain.auth.repository.SessionRepository;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
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
import app.mockly.domain.user.controller.docs.UserDocs;
import app.mockly.domain.user.dto.request.SaveUserAgreementsRequest;
import app.mockly.domain.user.dto.request.UpdateUserProfileRequest;
import app.mockly.domain.user.repository.UserAgreementRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
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

import java.time.Instant;
import java.util.List;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAgreementRepository userAgreementRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private SubscriptionProductRepository subscriptionProductRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private PortOneService portOneService;

    private User testUser;
    private String validAccessToken;
    private String validRefreshToken;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("member@mockly.app")
                .name("기존닉네임")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("google-member-001")
                .build());

        validAccessToken = jwtService.generateAccessToken(testUser.getId());
        validRefreshToken = jwtService.generateRefreshToken();

        Session session = sessionRepository.save(
                Session.create(testUser, new DeviceInfo("member-device-1", "Pixel 9"), new LocationInfo(37.5, 127.0))
        );
        RefreshToken rt = refreshTokenRepository.save(RefreshToken.builder()
                .token(validRefreshToken)
                .expiresAt(Instant.now().plusMillis(604800000))
                .build());
        session.updateRefreshToken(rt);

        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);
    }

    @Test
    @DisplayName("POST /api/users/agreements - 성공: 필수 약관 포함 동의 저장 시 termsAgreedAt이 갱신된다")
    void saveUserAgreements_Success() throws Exception {
        SaveUserAgreementsRequest request = new SaveUserAgreementsRequest(List.of(
                new SaveUserAgreementsRequest.AgreementItem("age14", true),
                new SaveUserAgreementsRequest.AgreementItem("terms", true),
                new SaveUserAgreementsRequest.AgreementItem("privacy", true),
                new SaveUserAgreementsRequest.AgreementItem("location", true),
                new SaveUserAgreementsRequest.AgreementItem("marketing", false)
        ));

        mockMvc.perform(post("/api/users/agreements")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andDo(document("users-agreements-save",
                        resource(UserDocs.saveAgreementsSuccess())
                ));

        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updated.hasAgreedToTerms()).isTrue();
        assertThat(userAgreementRepository.findByUserId(testUser.getId())).hasSize(5);
    }

    @Test
    @DisplayName("POST /api/users/agreements - 실패: 필수 약관 미동의 시 400 VALIDATION_ERROR")
    void saveUserAgreements_MissingRequired() throws Exception {
        SaveUserAgreementsRequest request = new SaveUserAgreementsRequest(List.of(
                new SaveUserAgreementsRequest.AgreementItem("age14", true),
                new SaveUserAgreementsRequest.AgreementItem("terms", true),
                new SaveUserAgreementsRequest.AgreementItem("privacy", false)
        ));

        mockMvc.perform(post("/api/users/agreements")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andDo(document("users-agreements-save-validation-error",
                        resource(UserDocs.saveAgreementsValidationError())
                ));
    }

    @Test
    @DisplayName("POST /api/users/agreements - 실패: 중복된 약관 항목 제출 시 400 VALIDATION_ERROR")
    void saveUserAgreements_DuplicateType() throws Exception {
        SaveUserAgreementsRequest request = new SaveUserAgreementsRequest(List.of(
                new SaveUserAgreementsRequest.AgreementItem("age14", true),
                new SaveUserAgreementsRequest.AgreementItem("terms", true),
                new SaveUserAgreementsRequest.AgreementItem("terms", false),
                new SaveUserAgreementsRequest.AgreementItem("privacy", true)
        ));

        mockMvc.perform(post("/api/users/agreements")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PATCH /api/users/me - 성공: 닉네임·희망 직군/직무·경력 연차 수정")
    void updateMyProfile_Success() throws Exception {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "새닉네임",
                "DEVELOPER",
                "FRONTEND",
                3
        );

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("새닉네임"))
                .andExpect(jsonPath("$.data.jobCategory").value("DEVELOPER"))
                .andExpect(jsonPath("$.data.jobRole").value("FRONTEND"))
                .andExpect(jsonPath("$.data.experienceYears").value(3))
                .andDo(document("users-me-update",
                        resource(UserDocs.updateMyProfileSuccess())
                ));
    }

    @Test
    @DisplayName("PATCH /api/users/me - 실패: 경력 연차 범위 초과 시 400 VALIDATION_ERROR")
    void updateMyProfile_ValidationError() throws Exception {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "새닉네임",
                "DEVELOPER",
                "FRONTEND",
                55
        );

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andDo(document("users-me-update-validation-error",
                        resource(UserDocs.updateMyProfileValidationError())
                ));
    }

    @Test
    @DisplayName("DELETE /api/users/{userId} - 성공: 구독 스케줄 취소·결제수단 비활성화·세션 삭제·토큰 블랙리스트·비식별화")
    void withdrawUser_Success() throws Exception {
        SubscriptionProduct basicProduct = subscriptionProductRepository.save(SubscriptionProduct.builder()
                .name("Basic")
                .description("베이직 플랜")
                .planTier(PlanTier.BASIC)
                .isActive(true)
                .build());
        SubscriptionPlan basicPlan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .product(basicProduct)
                .price(new java.math.BigDecimal("5900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());
        Subscription paidSubscription = Subscription.create(testUser.getId(), basicPlan);
        paidSubscription.activate();
        paidSubscription.setCurrentPaymentScheduleId("schedule-withdraw-123");
        paidSubscription = subscriptionRepository.save(paidSubscription);

        PaymentMethod paymentMethod = paymentMethodRepository.save(
                PaymentMethod.create(testUser, "billing-key-withdraw", "1234-****-****-5678", "SHINHAN", true)
        );

        mockMvc.perform(delete("/api/users/{userId}", testUser.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andDo(document("users-withdraw",
                        resource(UserDocs.withdrawSuccess())
                ));

        verify(portOneService).revokePaymentSchedule("schedule-withdraw-123");
        verify(tokenBlacklistService).save(eq(validAccessToken), anyLong());

        Subscription canceledSubscription = subscriptionRepository.findById(paidSubscription.getId()).orElseThrow();
        assertThat(canceledSubscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(canceledSubscription.getCurrentMarker()).isNull();
        assertThat(canceledSubscription.getCurrentPaymentScheduleId()).isNull();

        PaymentMethod deactivatedMethod = paymentMethodRepository.findById(paymentMethod.getId()).orElseThrow();
        assertThat(deactivatedMethod.isActive()).isFalse();
        assertThat(deactivatedMethod.isDefault()).isFalse();

        assertThat(sessionRepository.findByUserIdOrderByLastAccessedAtDesc(testUser.getId())).isEmpty();
        assertThat(refreshTokenRepository.findByToken(validRefreshToken)).isEmpty();

        User withdrawn = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(withdrawn.isDeleted()).isTrue();
        assertThat(withdrawn.getName()).isEqualTo(User.WITHDRAWN_USER_NAME);
        assertThat(withdrawn.getEmail()).isEqualTo("deleted-" + testUser.getId() + "@withdrawn.mockly.app");
    }

    @Test
    @DisplayName("DELETE /api/users/{userId} - 실패: PortOne 결제 스케줄 취소 실패 시 502 반환 및 계정·구독 상태 보존")
    void withdrawUser_PortOneRevokeFailure_AbortsWithdrawalWith502() throws Exception {
        SubscriptionProduct basicProduct = subscriptionProductRepository.save(SubscriptionProduct.builder()
                .name("Basic")
                .description("베이직 플랜")
                .planTier(PlanTier.BASIC)
                .isActive(true)
                .build());
        SubscriptionPlan basicPlan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .product(basicProduct)
                .price(new java.math.BigDecimal("5900"))
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());
        Subscription paidSubscription = Subscription.create(testUser.getId(), basicPlan);
        paidSubscription.activate();
        paidSubscription.setCurrentPaymentScheduleId("schedule-fail-502");
        paidSubscription = subscriptionRepository.save(paidSubscription);

        PaymentMethod paymentMethod = paymentMethodRepository.save(
                PaymentMethod.create(testUser, "billing-key-keep", "1234-****-****-5678", "SHINHAN", true)
        );

        doThrow(new BusinessException(ApiStatusCode.INTERNAL_SERVER_ERROR, "결제 예약 취소 도중 오류가 발생했습니다."))
                .when(portOneService)
                .revokePaymentSchedule("schedule-fail-502");

        mockMvc.perform(delete("/api/users/{userId}", testUser.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("EXTERNAL_PAYMENT_ERROR"));

        verify(tokenBlacklistService, never()).save(anyString(), anyLong());

        User unchangedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(unchangedUser.isDeleted()).isFalse();

        Subscription unchangedSubscription = subscriptionRepository.findById(paidSubscription.getId()).orElseThrow();
        assertThat(unchangedSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(unchangedSubscription.getCurrentPaymentScheduleId()).isEqualTo("schedule-fail-502");

        PaymentMethod unchangedMethod = paymentMethodRepository.findById(paymentMethod.getId()).orElseThrow();
        assertThat(unchangedMethod.isActive()).isTrue();
        assertThat(sessionRepository.findByUserIdOrderByLastAccessedAtDesc(testUser.getId())).isNotEmpty();
        assertThat(refreshTokenRepository.findByToken(validRefreshToken)).isPresent();
    }

    @Test
    @DisplayName("DELETE /api/users/{userId} - 실패: 타인 계정 탈퇴 요청 시 403 FORBIDDEN")
    void withdrawUser_Forbidden() throws Exception {
        User anotherUser = userRepository.save(User.builder()
                .email("other@mockly.app")
                .name("다른유저")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("google-other-999")
                .build());

        mockMvc.perform(delete("/api/users/{userId}", anotherUser.getId())
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andDo(document("users-withdraw-forbidden",
                        resource(UserDocs.withdrawForbidden())
                ));
    }
}
