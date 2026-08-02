package app.mockly.domain.auth.controller;

import app.mockly.domain.auth.dto.request.DevLoginRequest;
import app.mockly.domain.auth.dto.response.LoginResponse;
import app.mockly.domain.auth.service.AuthService;
import app.mockly.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
@RequestMapping("/api/auth/dev")
@RequiredArgsConstructor
public class DevAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> devLogin(@RequestBody DevLoginRequest request) {
        LoginResponse loginResponse = authService.loginWithDev(request);
        return ResponseEntity.ok(ApiResponse.success(loginResponse));
    }
}
