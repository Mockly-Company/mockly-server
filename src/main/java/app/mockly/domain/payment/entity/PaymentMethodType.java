package app.mockly.domain.payment.entity;

import lombok.Getter;

@Getter
public enum PaymentMethodType {
    CARD("카드"),
    EASY_PAY("간편결제"),
    MOBILE("휴대폰"),
    UNKNOWN("알 수 없음");

    private final String displayName;

    PaymentMethodType(String displayName) {
        this.displayName = displayName;
    }
}
