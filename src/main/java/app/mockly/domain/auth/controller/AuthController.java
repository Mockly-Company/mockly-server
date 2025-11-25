package app.mockly.domain.auth.controller;

import app.mockly.domain.auth.dto.UserInfo;
import app.mockly.domain.auth.dto.request.AuthorizationCodeRequest;
import app.mockly.domain.auth.dto.request.LoginWithGoogleRequest;
import app.mockly.domain.auth.dto.response.LoginResponse;
import app.mockly.domain.auth.service.AuthService;
import app.mockly.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    /**
     * @deprecated
     * OAuth 2.1을 준수하지 않습니다.
     * 테스트 목적으로만 사용하세요.
     * */
    @PostMapping("/login/google")
    public ResponseEntity<LoginResponse> loginWithGoogle(@Valid @RequestBody LoginWithGoogleRequest request) {
        LoginResponse loginResponse = authService.loginWithGoogle(request.idToken());
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/login/google/code")
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogleCode(@Valid @RequestBody AuthorizationCodeRequest request) {
        LoginResponse loginResponse = authService
                .loginWithGoogleCode(request.code(), request.codeVerifier(), request.redirectUri());
        return ResponseEntity.ok(ApiResponse.success(loginResponse));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfo>> getCurrentUsesr(@AuthenticationPrincipal UUID userId) {
        UserInfo userInfo = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }
}
