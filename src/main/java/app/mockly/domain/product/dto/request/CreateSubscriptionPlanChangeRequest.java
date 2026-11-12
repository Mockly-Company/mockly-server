package app.mockly.domain.product.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateSubscriptionPlanChangeRequest(
        @NotNull(message = "대상 플랜 ID는 필수입니다.")
        Integer targetPlanId,
        @NotNull(message = "확인된 금액은 필수입니다.")
        @PositiveOrZero(message = "확인된 금액은 0 이상이어야 합니다.")
        BigDecimal confirmedAmount
) {
}
