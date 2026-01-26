package app.mockly.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateSubscriptionRequest(
        @NotNull Integer planId,
        @NotNull @Positive BigDecimal expectedPrice,
        @NotBlank String billingKey
        ) {
}
