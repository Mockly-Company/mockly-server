package app.mockly.domain.plan.entity;

import lombok.Getter;

@Getter
public enum BillingCycle {
    MONTHLY("월간"),
    YEARLY("연간");

    private final String description;

    BillingCycle(String description) {
        this.description = description;
    }
}
