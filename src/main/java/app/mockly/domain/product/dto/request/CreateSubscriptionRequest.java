package app.mockly.domain.product.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateSubscriptionRequest(
        @NotNull Integer planId
) {
}
