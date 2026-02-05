package app.mockly.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddPaymentMethodRequest(
        @NotBlank(message = "빌링키는 필수입니다.")
        String billingKey
) {
}
