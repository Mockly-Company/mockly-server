package app.mockly.domain.payment.entity;

import io.portone.sdk.server.payment.PaymentMethod;
import io.portone.sdk.server.payment.PaymentMethodCard;
import io.portone.sdk.server.payment.PaymentMethodEasyPay;
import io.portone.sdk.server.payment.PaymentMethodMobile;
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

    public static PaymentMethodType from(PaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            return UNKNOWN;
        }
        return switch (paymentMethod) {
            case PaymentMethodCard card -> CARD;
            case PaymentMethodEasyPay easyPay -> EASY_PAY;
            case PaymentMethodMobile mobile -> MOBILE;
            default -> UNKNOWN;
        };
    }
}
