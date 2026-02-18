package app.mockly.domain.product.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdatePaymentMethodRequest(
        @NotNull Long paymentMethodId
) {
}
