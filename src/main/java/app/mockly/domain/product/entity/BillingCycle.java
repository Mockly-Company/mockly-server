package app.mockly.domain.product.entity;

import lombok.Getter;

@Getter
public enum BillingCycle {
    MONTHLY("월간"),
    YEARLY("연간"),
    LIFETIME("평생");

    private final String description;

    BillingCycle(String description) {
        this.description = description;
    }
}
