package app.mockly.domain.auth.controller;

import app.mockly.domain.auth.controller.docs.AuthMeDocs;
import app.mockly.domain.auth.controller.docs.LoginWithAppleCodeDocs;
import app.mockly.domain.auth.controller.docs.LoginWithGoogleCodeDocs;
import app.mockly.domain.auth.controller.docs.LogoutDocs;
import app.mockly.domain.auth.controller.docs.RefreshTokenDocs;
import app.mockly.domain.auth.dto.AppleUser;
import app.mockly.domain.auth.dto.DeviceInfo;
import app.mockly.domain.auth.dto.GoogleUser;
import app.mockly.domain.auth.dto.LocationInfo;
import app.mockly.domain.auth.dto.Platform;
import app.mockly.domain.auth.dto.request.AuthorizationCodeRequest;
import app.mockly.domain.auth.dto.request.LoginWithAppleCodeRequest;
import app.mockly.domain.auth.dto.request.LogoutRequest;
import app.mockly.domain.auth.dto.request.RefreshTokenRequest;
import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.RefreshToken;
import app.mockly.domain.auth.entity.Session;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.RefreshTokenRepository;
import app.mockly.domain.auth.repository.SessionRepository;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.AppleOAuthService;
import app.mockly.domain.auth.service.AuthService;
import app.mockly.domain.auth.service.GoogleOAuthService;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.product.entity.BillingCycle;
import app.mockly.domain.product.entity.Currency;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.entity.SubscriptionPlan;
import app.mockly.domain.product.entity.SubscriptionProduct;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.InvalidTokenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @MockitoBean
    private GoogleOAuthService googleOAuthService;

    @MockitoBean
    private AppleOAuthService appleOAuthService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

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

    private User testUser;
    private String validAccessToken;
    private String validRefreshTokenValue;
    private Session validSession;
    private DeviceInfo deviceInfo;
    private LocationInfo locationInfo;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .name("테스트 사용자")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("test-google-provider-id")
                .jobCategory("DEVELOPER")
                .jobRole("BACKEND")
                .experienceYears(2)
                .termsAgreedAt(Instant.parse("2026-04-01T00:00:00Z"))
                .build();
        testUser = userRepository.save(testUser);

        validAccessToken = jwtService.generateAccessToken(testUser.getId());
        validRefreshTokenValue = jwtService.generateRefreshToken();

        deviceInfo = new DeviceInfo("test-device-uuid", "Test Device");
        locationInfo = new LocationInfo(37.0, 127.0);
        validSession = sessionRepository.save(Session.create(testUser, deviceInfo, locationInfo));

        RefreshToken refreshToken = RefreshToken.builder()
                .token(validRefreshTokenValue)
                .expiresAt(Instant.now().plusMillis(604800000))
                .build();
        refreshToken = refreshTokenRepository.save(refreshToken);
        validSession.updateRefreshToken(refreshToken);

        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);
    }

    @Test
    @DisplayName("POST /api/auth/login/google/code - Google OAuth 로그인")
    void loginWithGoogleCode() throws Exception {
        GoogleUser mockGoogleUser = new GoogleUser(
                "google-user-id-123",
                "user@mockly.com",
                "Mockly User",
                true
        );

        given(googleOAuthService.exchangeAuthorizationCode(anyString(), anyString(), anyString(), any()))
                .willReturn("mock-id-token");
        given(googleOAuthService.verifyIdToken(anyString()))
                .willReturn(mockGoogleUser);

        AuthorizationCodeRequest request = new AuthorizationCodeRequest(
                "test-auth-code",
                "test-code-verifier",
                "https://mockly.app/oauth2/google/redirect",
                new DeviceInfo("uuid-from-client", "device-name"),
                new LocationInfo(127.0, 135.0)
        );

        mockMvc.perform(post("/api/auth/login/google/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.user.email").value("user@mockly.com"))
                .andExpect(jsonPath("$.data.user.name").value("Mockly User"))
                .andExpect(jsonPath("$.data.user.hasAgreedToTerms").value(false))
                .andDo(document("auth-login-google-code",
                        resource(LoginWithGoogleCodeDocs.success())
                ));
    }

    @Test
    @DisplayName("POST /api/auth/login/google/code - User-Agent로 플랫폼을 판별해 교환한다")
    void loginWithGoogleCode_ResolvesPlatform() throws Exception {
        given(googleOAuthService.exchangeAuthorizationCode(anyString(), anyString(), anyString(), any()))
                .willReturn("mock-id-token");
        given(googleOAuthService.verifyIdToken(anyString()))
                .willReturn(new GoogleUser("google-user-id-456", "ios@mockly.com", "iOS User", true));

        String requestBody = """
                {
                  "code": "test-auth-code",
                  "codeVerifier": "test-code-verifier",
                  "redirectUri": "https://mockly.app/oauth2/google/redirect",
                  "deviceInfo": {"deviceId": "%s", "deviceName": "device-name"}
                }
                """;

        mockMvc.perform(post("/api/auth/login/google/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "okhttp/4.12.0")
                        .content(requestBody.formatted("android-device")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login/google/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "mockly/1 CFNetwork/3860.100.1 Darwin/25.5.0")
                        .content(requestBody.formatted("ios-device")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login/google/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.formatted("no-ua-device")))
                .andExpect(status().isOk());

        ArgumentCaptor<Platform> platformCaptor = ArgumentCaptor.forClass(Platform.class);
        verify(googleOAuthService, times(3))
                .exchangeAuthorizationCode(anyString(), anyString(), anyString(), platformCaptor.capture());
        assertThat(platformCaptor.getAllValues())
                .containsExactly(Platform.ANDROID, Platform.IOS, Platform.ANDROID);
    }

    @Test
    @DisplayName("POST /api/auth/login/apple/code - Apple OAuth 최초 로그인 시 신규 회원 생성 및 Free 플랜 자동 배정")
    void loginWithAppleCode_Success() throws Exception {
        SubscriptionProduct freeProduct = subscriptionProductRepository.save(SubscriptionProduct.builder()
                .name("Free")
                .description("무료 플랜")
                .planTier(PlanTier.FREE)
                .isActive(true)
                .build());
        subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .product(freeProduct)
                .price(java.math.BigDecimal.ZERO)
                .currency(Currency.KRW)
                .billingCycle(BillingCycle.MONTHLY)
                .build());

        AppleUser mockAppleUser = new AppleUser(
                "apple-sub-001",
                "appleuser@privaterelay.appleid.com",
                true
        );

        given(appleOAuthService.exchangeAuthorizationCode(anyString(), anyString(), anyString()))
                .willReturn("mock-apple-id-token");
        given(appleOAuthService.verifyIdToken(anyString()))
                .willReturn(mockAppleUser);

        LoginWithAppleCodeRequest request = new LoginWithAppleCodeRequest(
                "apple-auth-code",
                "apple-code-verifier",
                "mockly://oauth/callback",
                new DeviceInfo("ios-device-uuid", "iPhone 16 Pro"),
                new LocationInfo(37.4979, 127.0276),
                "홍길동"
        );

        mockMvc.perform(post("/api/auth/login/apple/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.user.email").value("appleuser@privaterelay.appleid.com"))
                .andExpect(jsonPath("$.data.user.name").value("홍길동"))
                .andExpect(jsonPath("$.data.user.hasAgreedToTerms").value(false))
                .andDo(document("auth-login-apple-code",
                        resource(LoginWithAppleCodeDocs.success())
                ));

        User created = userRepository.findByProviderAndProviderId(OAuth2Provider.APPLE, "apple-sub-001").orElseThrow();
        assertThat(subscriptionRepository.findCurrentByUserId(created.getId()))
                .isPresent()
                .get()
                .satisfies(sub -> assertThat(sub.getSubscriptionPlan().getProduct().getPlanTier()).isEqualTo(PlanTier.FREE));
    }

    @Test
    @DisplayName("POST /api/auth/login/apple/code - 동일 이메일 기존 회원이 있으면 일관된 계정으로 로그인된다")
    void loginWithAppleCode_LinksExistingEmailUser() throws Exception {
        AppleUser mockAppleUser = new AppleUser(
                "apple-sub-same-email",
                "test@example.com",
                true
        );

        given(appleOAuthService.exchangeAuthorizationCode(anyString(), anyString(), anyString()))
                .willReturn("mock-apple-id-token");
        given(appleOAuthService.verifyIdToken(anyString()))
                .willReturn(mockAppleUser);

        LoginWithAppleCodeRequest request = new LoginWithAppleCodeRequest(
                "apple-auth-code-2",
                "apple-code-verifier-2",
                "mockly://oauth/callback",
                new DeviceInfo("ios-device-shared", "iPhone 16 Pro"),
                null,
                null
        );

        mockMvc.perform(post("/api/auth/login/apple/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.data.user.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/login/apple/code - 실패: 유효하지 않은 Apple 인증 코드")
    void loginWithAppleCode_InvalidToken() throws Exception {
        given(appleOAuthService.exchangeAuthorizationCode(anyString(), anyString(), anyString()))
                .willThrow(new InvalidTokenException(ApiStatusCode.INVALID_APPLE_TOKEN, "Apple 인증 코드가 유효하지 않습니다"));

        LoginWithAppleCodeRequest request = new LoginWithAppleCodeRequest(
                "invalid-apple-code",
                "apple-code-verifier",
                "mockly://oauth/callback",
                new DeviceInfo("ios-device-uuid", "iPhone 16 Pro"),
                new LocationInfo(37.4979, 127.0276),
                "홍길동"
        );

        mockMvc.perform(post("/api/auth/login/apple/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("INVALID_APPLE_TOKEN"))
                .andDo(document("auth-login-apple-code-invalid-token",
                        resource(LoginWithAppleCodeDocs.invalidToken())
                ));
    }

    @Test
    @DisplayName("GET /api/auth/me - 성공: 유효한 JWT로 현재 사용자 프로필 및 약관 동의 여부 조회")
    void getCurrentUser_Success() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.name").value("테스트 사용자"))
                .andExpect(jsonPath("$.data.hasAgreedToTerms").value(true))
                .andExpect(jsonPath("$.data.jobCategory").value("DEVELOPER"))
                .andExpect(jsonPath("$.data.jobRole").value("BACKEND"))
                .andExpect(jsonPath("$.data.experienceYears").value(2))
                .andDo(document("auth-me",
                        resource(AuthMeDocs.success())
                ));
    }

    @Test
    @DisplayName("GET /api/auth/me - 실패: Authorization 헤더 없음")
    void getCurrentUser_NoToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andDo(document("auth-me-no-token",
                        resource(AuthMeDocs.noToken())
                ));
    }

    @Test
    @DisplayName("GET /api/auth/me - 실패: 잘못된 JWT 토큰")
    void getCurrentUser_InvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.token.here")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"))
                .andDo(document("auth-me-invalid-token",
                        resource(AuthMeDocs.invalidToken())
                ));
    }

    @Test
    @DisplayName("GET /api/auth/me - 실패: 존재하지 않는 사용자")
    void getCurrentUser_UserNotFound() throws Exception {
        UUID ghostUserId = UUID.randomUUID();
        String tokenWithNonExistentUser = jwtService.generateAccessToken(ghostUserId);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokenWithNonExistentUser)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"))
                .andDo(document("auth-me-user-not-found",
                        resource(AuthMeDocs.userNotFound())
                ));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - 성공")
    void refreshToken_Success() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest(validRefreshTokenValue, locationInfo);

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Device-Id", deviceInfo.deviceId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andDo(document("refresh-tokens",
                        resource(RefreshTokenDocs.success())
                ));
    }

    @Test
    @DisplayName("POST /api/auth/logout - 성공")
    void logout_Success() throws Exception {
        LogoutRequest request = new LogoutRequest(validRefreshTokenValue);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + validAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andDo(document("auth-logout",
                        resource(LogoutDocs.success())
                ));
    }
}