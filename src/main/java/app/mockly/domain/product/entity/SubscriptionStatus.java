package app.mockly.domain.product.entity;

import lombok.Getter;

@Getter
public enum SubscriptionStatus {
    PENDING("대기"),
    ACTIVE("활성"),
    CANCELED("해지"),
    EXPIRED("만료");

    private final String description;

    SubscriptionStatus(String description) {
        this.description = description;
    }
}
