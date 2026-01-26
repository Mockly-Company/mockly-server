package app.mockly.domain.product.controller;

import app.mockly.domain.product.dto.request.CreateSubscriptionRequest;
import app.mockly.domain.product.dto.response.CancelSubscriptionResponse;
import app.mockly.domain.product.dto.response.CreateSubscriptionResponse;
import app.mockly.domain.product.dto.response.GetSubscriptionResponse;
import app.mockly.domain.product.service.SubscriptionService;
import app.mockly.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateSubscriptionResponse>> createSubscription(
            @RequestBody @Valid CreateSubscriptionRequest request,
            @AuthenticationPrincipal UUID userId) {
        CreateSubscriptionResponse response = subscriptionService.createSubscription(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetSubscriptionResponse>> getSubscription(
            @AuthenticationPrincipal UUID userId) {
        GetSubscriptionResponse response = subscriptionService.getMySubscription(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<CancelSubscriptionResponse>> cancelSubscription(
            @PathVariable Long id,
            @AuthenticationPrincipal UUID userId) {
        CancelSubscriptionResponse response = subscriptionService.cancelSubscription(userId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
