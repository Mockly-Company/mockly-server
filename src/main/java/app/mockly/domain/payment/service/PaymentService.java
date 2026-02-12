package app.mockly.domain.payment.service;

import app.mockly.domain.payment.dto.response.GetInvoiceDetailResponse;
import app.mockly.domain.payment.dto.response.GetPaymentDetailResponse;
import app.mockly.domain.payment.dto.response.GetPaymentsResponse;
import app.mockly.domain.payment.entity.Invoice;
import app.mockly.domain.payment.entity.Payment;
import app.mockly.domain.payment.entity.PaymentStatus;
import app.mockly.domain.payment.repository.PaymentRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {
    private static final int MAX_PAGE_SIZE = 20;

    private final PaymentRepository paymentRepository;

    public GetPaymentsResponse getPayments(UUID userId, int page, int size,
                                           PaymentStatus status,
                                           LocalDate startDate, LocalDate endDate) {
        Pageable pageable = PageRequest.of(page - 1, Math.min(size, MAX_PAGE_SIZE));

        boolean hasStatus = status != null;
        boolean hasDateRange = startDate != null && endDate != null;

        Page<Payment> paymentPage;
        if (hasStatus && hasDateRange) {
            paymentPage = paymentRepository.findByUserIdAndStatusAndCreatedAtBetween(userId, status, toStartOfDay(startDate), toEndOfDay(endDate), pageable);
        } else if (hasStatus) {
            paymentPage = paymentRepository.findByUserIdAndStatus(userId, status, pageable);
        } else if (hasDateRange) {
            paymentPage = paymentRepository.findByUserIdAndCreatedAtBetween(userId, toStartOfDay(startDate), toEndOfDay(endDate), pageable);
        } else {
            paymentPage = paymentRepository.findByUserId(userId, pageable);
        }
        return GetPaymentsResponse.from(paymentPage);
    }

    private Instant toStartOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant toEndOfDay(LocalDate date) { // 다음날의 시작
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public GetPaymentDetailResponse getPaymentDetail(UUID userId, String paymentId) {
        Payment payment = paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "결제 내역을 찾을 수 없습니다."));
        return GetPaymentDetailResponse.from(payment);
    }

    public GetInvoiceDetailResponse getInvoiceDetail(UUID userId, String paymentId) {
        Payment payment = paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "결제 내역을 찾을 수 없습니다."));
        Invoice invoice = payment.getInvoice();
        return GetInvoiceDetailResponse.from(invoice);
    }
}
