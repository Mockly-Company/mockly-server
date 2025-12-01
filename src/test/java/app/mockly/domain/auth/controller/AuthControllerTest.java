package app.mockly.domain.auth.controller;

import app.mockly.domain.auth.dto.DeviceInfo;
import app.mockly.domain.auth.dto.GoogleUser;
import app.mockly.domain.auth.dto.LocationInfo;
import app.mockly.domain.auth.dto.request.AuthorizationCodeRequest;
import app.mockly.domain.auth.dto.request.LogoutRequest;
import app.mockly.domain.auth.dto.request.RefreshTokenRequest;
import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.RefreshToken;
import app.mockly.domain.auth.entity.Session;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.RefreshTokenRepository;
import app.mockly.domain.auth.repository.SessionRepository;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.AuthService;
import app.mockly.domain.auth.service.GoogleOAuthService;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
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
import java.util.UUID;

import app.mockly.domain.auth.controller.docs.AuthMeDocs;
import app.mockly.domain.auth.controller.docs.LoginWithGoogleCodeDocs;
import app.mockly.domain.auth.controller.docs.LogoutDocs;
import app.mockly.domain.auth.controller.docs.RefreshTokenDocs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional // 테스트 후 롤백
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
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

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
                .build();
        testUser = userRepository.save(testUser);  // save() 반환값 사용 (ID가 생성됨)

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

        // TokenBlacklistService 모킹 - 모든 토큰을 블랙리스트에 없다고 응답
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

        given(googleOAuthService.exchangeAuthorizationCode(anyString(), anyString(), anyString()))
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

        // when & then
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
                .andDo(document("auth-login-google-code",
                        resource(LoginWithGoogleCodeDocs.success())
                ));
    }

    @Test
    @DisplayName("GET /api/auth/me - 성공: 유효한 JWT로 현재 사용자 조회")
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
        RefreshTokenRequest request = new RefreshTokenRequest(validRefreshTokenValue, deviceInfo, locationInfo);

        mockMvc.perform(post("/api/auth/refresh")
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