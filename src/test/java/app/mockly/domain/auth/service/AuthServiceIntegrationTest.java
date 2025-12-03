package app.mockly.domain.auth.service;

import app.mockly.domain.auth.dto.DeviceInfo;
import app.mockly.domain.auth.dto.LocationInfo;
import app.mockly.domain.auth.dto.request.DevLoginRequest;
import app.mockly.domain.auth.dto.response.LoginResponse;
import app.mockly.domain.auth.entity.Session;
import app.mockly.domain.auth.repository.RefreshTokenRepository;
import app.mockly.domain.auth.repository.SessionRepository;
import app.mockly.domain.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@Transactional
@DisplayName("AuthService 통합 테스트")
public class AuthServiceIntegrationTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @Test
    @DisplayName("로그아웃 후 같은 디바이스로 재로그인 시, 세션 재사용")
    void logout_and_relogin_reuses_session() {
        String email = "test@example.com";
        String deviceId = "test-device-123";
        DeviceInfo deviceInfo = new DeviceInfo(deviceId, "test device");
        LocationInfo locationInfo = new LocationInfo(37.5, 127.0);
        DevLoginRequest request = new DevLoginRequest(email, "test user", deviceInfo, locationInfo);

        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);

        // 1) 로그인
        LoginResponse loginResponse1 = authService.loginWithDev(request);
        Optional<Session> sessionAfterLogin1 = sessionRepository.findByUserAndDeviceId(userRepository.findByEmail(email).get(), deviceId);
        assertThat(sessionAfterLogin1).isPresent();

        // 2) 로그아웃
        authService.logout(loginResponse1.accessToken(), loginResponse1.refreshToken());

        // 3) 재로그인
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

        // 1) 로그인
        LoginResponse loginResponse1 = authService.loginWithDev(request);
        String refreshToken1 = loginResponse1.refreshToken();
        Long sessionId1 = sessionRepository.findByUserAndDeviceId(userRepository.findByEmail(email).get(), deviceId).get().getId();

        // 2) 로그아웃 없이 재로그인
        LoginResponse loginResponse2 = authService.loginWithDev(request);
        String refreshToken2 = loginResponse2.refreshToken();
        Long sessionId2 = sessionRepository.findByUserAndDeviceId(userRepository.findByEmail(email).get(), deviceId).get().getId();

        assertThat(sessionId1).isEqualTo(sessionId2);
        assertThat(refreshToken1).isNotEqualTo(refreshToken2);

        assertThat(refreshTokenRepository.findByToken(refreshToken1)).isEmpty();
        assertThat(refreshTokenRepository.findByToken(refreshToken2)).isPresent();
    }
}
