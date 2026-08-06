package app.mockly.domain.auth.controller;

import app.mockly.domain.auth.dto.Platform;
import app.mockly.domain.auth.dto.UserInfo;
import app.mockly.domain.auth.dto.request.*;
import app.mockly.domain.auth.dto.response.LoginResponse;
import app.mockly.domain.auth.dto.response.RefreshTokenResponse;
import app.mockly.domain.auth.service.AuthService;
import app.mockly.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login/google/code")
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogleCode(
            @Valid @RequestBody AuthorizationCodeRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        Platform platform = Platform.fromUserAgent(userAgent);
        log.info("Google 로그인 플랫폼 판별: platform={} userAgent={}", platform, userAgent);

        LoginResponse loginResponse = authService.loginWithGoogleCode(
                request.code(),
                request.codeVerifier(),
                request.redirectUri(),
                request.deviceInfo(),
                request.locationInfo(),
                platform);
        return ResponseEntity.ok(ApiResponse.success(loginResponse));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfo>> getCurrentUser(@AuthenticationPrincipal UUID userId) {
        UserInfo userInfo = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(
            @RequestHeader("X-Device-Id") String deviceId,
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        RefreshTokenResponse refreshTokenResponse = authService.refreshToken(
                request.refreshToken(), deviceId, request.locationInfo());
        return ResponseEntity.ok(ApiResponse.success(refreshTokenResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody LogoutRequest request
    ) {
        String accessToken = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            accessToken = authorization.substring(7);
        }
        authService.logout(accessToken, request.refreshToken());
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}
