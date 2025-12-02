package app.mockly.domain.auth.controller;

import app.mockly.domain.auth.dto.UserInfo;
import app.mockly.domain.auth.dto.request.*;
import app.mockly.domain.auth.dto.response.LoginResponse;
import app.mockly.domain.auth.dto.response.RefreshTokenResponse;
import app.mockly.domain.auth.service.AuthService;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login/google/code")
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogleCode(@Valid @RequestBody AuthorizationCodeRequest request) {
        LoginResponse loginResponse = authService.loginWithGoogleCode(
                request.code(),
                request.codeVerifier(),
                request.redirectUri(),
                request.deviceInfo(),
                request.locationInfo());
        return ResponseEntity.ok(ApiResponse.success(loginResponse));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfo>> getCurrentUser(@AuthenticationPrincipal UUID userId) {
        UserInfo userInfo = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenResponse refreshTokenResponse = authService.refreshToken(
                request.refreshToken(), request.deviceInfo(), request.locationInfo());
        return ResponseEntity.ok(ApiResponse.success(refreshTokenResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UUID userId,
            @RequestHeader("Authorization") String authorization,
            @RequestBody LogoutRequest request
    ) {
        String accessToken = authorization.substring(7);
        authService.logout(accessToken, request.refreshToken());
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @PostMapping("/dev/login")
    @Profile("!prod")
    public ResponseEntity<ApiResponse<LoginResponse>> devLogin(@RequestBody DevLoginRequest request) {
        LoginResponse loginResponse = authService.loginWithDev(request);
        return ResponseEntity.ok(ApiResponse.success(loginResponse));
    }
}
