package app.mockly.domain.payment.controller;

import app.mockly.domain.payment.dto.response.GetPaymentDetailResponse;
import app.mockly.domain.payment.dto.response.GetPaymentsResponse;
import app.mockly.domain.payment.entity.PaymentStatus;
import app.mockly.domain.payment.service.PaymentQueryService;
import app.mockly.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentQueryService paymentQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<GetPaymentsResponse>> getPayments(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate
    ) {
        GetPaymentsResponse response = paymentQueryService.getPayments(
                userId, page, size, status, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<GetPaymentDetailResponse>> getPaymentDetail(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String paymentId
    ) {
        GetPaymentDetailResponse response = paymentQueryService.getPaymentDetail(userId, paymentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
