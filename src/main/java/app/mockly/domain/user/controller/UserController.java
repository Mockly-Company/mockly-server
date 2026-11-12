package app.mockly.domain.user.controller;

import app.mockly.domain.auth.dto.UserInfo;
import app.mockly.domain.user.dto.request.SaveUserAgreementsRequest;
import app.mockly.domain.user.dto.request.UpdateUserProfileRequest;
import app.mockly.domain.user.service.UserService;
import app.mockly.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/agreements")
    public ResponseEntity<ApiResponse<Void>> saveUserAgreements(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody SaveUserAgreementsRequest request
    ) {
        userService.saveUserAgreements(userId, request);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserInfo>> updateMyProfile(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        UserInfo updated = userService.updateMyProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> withdrawUser(
            @AuthenticationPrincipal UUID authenticatedUserId,
            @PathVariable UUID userId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String accessToken = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            accessToken = authorization.substring(7);
        }
        userService.withdrawUser(authenticatedUserId, userId, accessToken);
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}
