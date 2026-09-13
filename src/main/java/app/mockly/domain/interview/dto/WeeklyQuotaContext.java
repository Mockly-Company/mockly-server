package app.mockly.domain.interview.dto;

import app.mockly.domain.product.entity.SubscriptionProduct;
import java.time.LocalDate;

public record WeeklyQuotaContext(
        SubscriptionProduct product,
        LocalDate periodStart,
        LocalDate nextResetAt,
        boolean available
) {

    public static WeeklyQuotaContext of(SubscriptionProduct product, LocalDate periodStart, boolean available) {
        return new WeeklyQuotaContext(product, periodStart, periodStart.plusDays(7), available);
    }
}
