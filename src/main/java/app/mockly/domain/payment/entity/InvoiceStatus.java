package app.mockly.domain.payment.entity;

import lombok.Getter;

@Getter
public enum InvoiceStatus {
    PENDING("대기"),
    PAID("완료"),
    FAILED("실패");

    private final String description;

    InvoiceStatus(String description) {
        this.description = description;
    }
}
