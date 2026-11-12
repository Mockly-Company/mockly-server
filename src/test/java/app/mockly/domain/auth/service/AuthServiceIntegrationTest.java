package app.mockly.domain.auth.service;

import app.mockly.domain.auth.dto.AppleUser;
import app.mockly.domain.auth.dto.DeviceInfo;
import app.mockly.domain.auth.dto.LocationInfo;
import app.mockly.domain.auth.dto.request.DevLoginRequest;
import app.mockly.domain.auth.dto.request.LoginWithAppleCodeRequest;
import app.mockly.domain.auth.dto.response.LoginResponse;
import app.mockly.domain.auth.entity.Session;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.RefreshTokenRepository;
import app.mockly.domain.auth.repository.SessionRepository;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@Transactional
@DisplayName("AuthService 통합 테스트")
public class AuthServiceIntegrationTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private AppleOAuthService appleOAuthService;

    @Test
    @DisplayName("로그아웃 후 같은 디바이스로 재로그인 시, 세션 재사용")
    void logout_and_relogin_reuses_session() {
        String email = "test@example.com";
        String deviceId = "test-device-123";
        DeviceInfo deviceInfo = new DeviceInfo(deviceId, "test device");
        LocationInfo locationInfo = new LocationInfo(37.5, 127.0);
        DevLoginRequest request = new DevLoginRequest(email, "test user", deviceInfo, locationInfo);

        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);

        LoginResponse loginResponse1 = authService.loginWithDev(request);
        Optional<Session> sessionAfterLogin1 = sessionRepository.findByUserAndDeviceId(userRepository.findByEmail(email).get(), deviceId);
        assertThat(sessionAfterLogin1).isPresent();

        authService.logout(loginResponse1.accessToken(), loginResponse1.refreshToken());

        LoginResponse loginResponse2 = authService.loginWithDev(request);
        Optional<Session> sessionAfterLogin2 = sessionRepository.findByUserAndDeviceId(userRepository.findByEmail(email).get(), deviceId);

        assertThat(sessionAfterLogin2.get().getId()).isEqualTo(sessionAfterLogin1.get().getId());
        assertThat(loginResponse2.refreshToken()).isNotEqualTo(loginResponse1.refreshToken());

        assertThat(refreshTokenRepository.findByToken(loginResponse1.refreshToken())).isEmpty();
        assertThat(refreshTokenRepository.findByToken(loginResponse2.refreshToken())).isPresent();
    }

    @Test
    @DisplayName("활성 세션에서 재로그인 시, 토큰 교체 및 old 토큰 삭제")
    void active_session_relogin_replaces_token() {
        String email = "test2@example.com";
        String deviceId = "test-device-456";
        DeviceInfo deviceInfo = new DeviceInfo(deviceId, "test device");
        LocationInfo locationInfo = new LocationInfo(37.5, 127.0);
        DevLoginRequest request = new DevLoginRequest(email, "test user", deviceInfo, locationInfo);

        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);

        LoginResponse loginResponse1 = authService.loginWithDev(request);
        String refreshToken1 = loginResponse1.refreshToken();
        Long sessionId1 = sessionRepository.findByUserAndDeviceId(userRepository.findByEmail(email).get(), deviceId).get().getId();

        LoginResponse loginResponse2 = authService.loginWithDev(request);
        String refreshToken2 = loginResponse2.refreshToken();
        Long sessionId2 = sessionRepository.findByUserAndDeviceId(userRepository.findByEmail(email).get(), deviceId).get().getId();

        assertThat(sessionId1).isEqualTo(sessionId2);
        assertThat(refreshToken1).isNotEqualTo(refreshToken2);

        assertThat(refreshTokenRepository.findByToken(refreshToken1)).isEmpty();
        assertThat(refreshTokenRepository.findByToken(refreshToken2)).isPresent();
    }

    @Test
    @DisplayName("동일 기기에서 로그아웃 후 다른 계정으로 로그인해도 DataIntegrityViolationException 없이 독립 세션이 생성된다")
    void different_users_can_login_on_same_device() {
        String sharedDeviceId = "shared-device-999";
        DeviceInfo deviceInfo = new DeviceInfo(sharedDeviceId, "Shared iPhone");
        LocationInfo locationInfo = new LocationInfo(37.5, 127.0);

        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);

        LoginResponse loginA = authService.loginWithDev(
                new DevLoginRequest("user-a@example.com", "User A", deviceInfo, locationInfo));
        authService.logout(loginA.accessToken(), loginA.refreshToken());

        LoginResponse loginB = authService.loginWithDev(
                new DevLoginRequest("user-b@example.com", "User B", deviceInfo, locationInfo));

        User userA = userRepository.findByEmail("user-a@example.com").orElseThrow();
        User userB = userRepository.findByEmail("user-b@example.com").orElseThrow();

        Session sessionA = sessionRepository.findByUserAndDeviceId(userA, sharedDeviceId).orElseThrow();
        Session sessionB = sessionRepository.findByUserAndDeviceId(userB, sharedDeviceId).orElseThrow();

        assertThat(sessionA.getId()).isNotEqualTo(sessionB.getId());
        assertThat(sessionB.getRefreshToken()).isNotNull();
    }

    @Test
    @DisplayName("회원 탈퇴 후 동일 Apple 소셜 계정으로 재가입 시 신규 계정과 Free 구독이 생성된다")
    void withdrawn_apple_user_can_re_signup_cleanly() {
        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);
        given(appleOAuthService.exchangeAuthorizationCode(anyString(), anyString(), anyString()))
                .willReturn("apple-id-token");
        given(appleOAuthService.verifyIdToken(anyString()))
                .willReturn(new AppleUser("apple-sub-rejoin", "rejoin@apple.com", true));

        LoginWithAppleCodeRequest appleReq = new LoginWithAppleCodeRequest(
                "code-1",
                "verifier-1",
                "mockly://oauth/callback",
                new DeviceInfo("rejoin-device", "iPhone 16"),
                null,
                "재가입유저"
        );

        LoginResponse firstLogin = authService.loginWithAppleCode(appleReq);
        UUID firstUserId = UUID.fromString(firstLogin.user().id());

        userService.withdrawUser(firstUserId, firstUserId, firstLogin.accessToken());

        LoginResponse secondLogin = authService.loginWithAppleCode(appleReq);
        UUID secondUserId = UUID.fromString(secondLogin.user().id());

        assertThat(secondUserId).isNotEqualTo(firstUserId);
        assertThat(secondLogin.user().email()).isEqualTo("rejoin@apple.com");
        assertThat(secondLogin.user().hasAgreedToTerms()).isFalse();
    }
}
