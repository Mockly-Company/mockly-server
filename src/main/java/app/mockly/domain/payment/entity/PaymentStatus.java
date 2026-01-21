package app.mockly.domain.payment.entity;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    PENDING("진행중"),
    PAID("완료"),
    FAILED("실패"),
    CANCELED("취소");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }
}
