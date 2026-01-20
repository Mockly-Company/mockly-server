package app.mockly.domain.product.controller;

import app.mockly.domain.product.dto.response.GetSubscriptionProductsResponse;
import app.mockly.domain.product.service.SubscriptionProductService;
import app.mockly.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/subscription-products")
@RequiredArgsConstructor
public class SubscriptionProductController {
    private final SubscriptionProductService subscriptionProductService;

    @GetMapping
    public ResponseEntity<ApiResponse<GetSubscriptionProductsResponse>> getSubscriptionProducts(
            @AuthenticationPrincipal UUID userId
    ) {
        GetSubscriptionProductsResponse response = subscriptionProductService.getAllSubscriptionProducts(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
