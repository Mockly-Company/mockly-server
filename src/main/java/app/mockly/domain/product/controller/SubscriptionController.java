package app.mockly.domain.product.controller;

import app.mockly.domain.product.dto.request.CancelSubscriptionRequest;
import app.mockly.domain.product.dto.request.CreateSubscriptionPlanChangeRequest;
import app.mockly.domain.product.dto.request.CreateSubscriptionRequest;
import app.mockly.domain.product.dto.request.UpdatePaymentMethodRequest;
import app.mockly.domain.product.dto.response.CancelSubscriptionResponse;
import app.mockly.domain.product.dto.response.CreateSubscriptionPlanChangeResponse;
import app.mockly.domain.product.dto.response.CreateSubscriptionResponse;
import app.mockly.domain.product.dto.response.GetSubscriptionPlanChangeResponse;
import app.mockly.domain.product.dto.response.GetSubscriptionResponse;
import app.mockly.domain.product.service.SubscriptionService;
import app.mockly.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
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

        try {
            subscriptionService.processScheduleCreation(response.id());
        } catch (Exception e) {
            log.error("스케줄 생성 실패, Outbox 재시도 예정 - subscriptionId: {}", response.id(), e);
        }

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
            @RequestBody(required = false) CancelSubscriptionRequest request,
            @AuthenticationPrincipal UUID userId) {
        boolean immediate = request != null && Boolean.TRUE.equals(request.immediate());
        CancelSubscriptionResponse response = subscriptionService.cancelSubscription(userId, id, immediate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}/payment-method")
    public ResponseEntity<ApiResponse<Void>> updatePaymentMethod(
            @PathVariable Long id,
            @RequestBody @Valid UpdatePaymentMethodRequest request,
            @AuthenticationPrincipal UUID userId) {
        subscriptionService.updatePaymentMethod(userId, id, request.paymentMethodId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/plan-change")
    public ResponseEntity<ApiResponse<GetSubscriptionPlanChangeResponse>> previewPlanChange(
            @RequestParam(required = false) Integer targetPlanId,
            @RequestParam(required = false) Integer targetPlan,
            @AuthenticationPrincipal UUID userId) {
        Integer resolvedTargetPlanId = targetPlanId != null ? targetPlanId : targetPlan;
        GetSubscriptionPlanChangeResponse response = subscriptionService.previewPlanChange(userId, resolvedTargetPlanId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/plan-change")
    public ResponseEntity<ApiResponse<CreateSubscriptionPlanChangeResponse>> changeSubscriptionPlan(
            @RequestBody @Valid CreateSubscriptionPlanChangeRequest request,
            @AuthenticationPrincipal UUID userId) {
        CreateSubscriptionPlanChangeResponse response = subscriptionService.changeSubscriptionPlan(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
