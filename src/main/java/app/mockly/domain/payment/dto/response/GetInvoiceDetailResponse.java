package app.mockly.domain.payment.dto.response;

import app.mockly.domain.payment.entity.Invoice;
import app.mockly.domain.payment.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GetInvoiceDetailResponse(
        String id,
        BigDecimal amount,
        String currency,
        InvoiceStatus status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime paidAt
) {
    public static GetInvoiceDetailResponse from(Invoice invoice) {
        return new GetInvoiceDetailResponse(
                invoice.getId(),
                invoice.getAmount(),
                invoice.getCurrency().name(),
                invoice.getStatus(),
                invoice.getPeriodStart(),
                invoice.getPeriodEnd(),
                invoice.getPaidAt()
        );
    }
}
