package app.mockly.domain.product.controller;

import app.mockly.domain.product.dto.request.CreateSubscriptionRequest;
import app.mockly.domain.product.dto.response.CreateSubscriptionResponse;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.service.SubscriptionService;
import app.mockly.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateSubscriptionResponse>> createSubscriptions(
            @RequestBody @Valid CreateSubscriptionRequest request,
            @AuthenticationPrincipal UUID userId) {
        CreateSubscriptionResponse response = subscriptionService.createSubscription(userId, request.planId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
