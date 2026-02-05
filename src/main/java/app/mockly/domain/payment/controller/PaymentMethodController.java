package app.mockly.domain.payment.controller;

import app.mockly.domain.payment.dto.request.AddPaymentMethodRequest;
import app.mockly.domain.payment.dto.response.PaymentMethodResponse;
import app.mockly.domain.payment.service.PaymentMethodService;
import app.mockly.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {
    private final PaymentMethodService paymentMethodService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentMethodResponse>> addPaymentMethod(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AddPaymentMethodRequest request
    ) {
        PaymentMethodResponse response = paymentMethodService.addPaymentMethod(userId, request.billingKey());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
