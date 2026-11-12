package app.mockly.domain.interview.entity;

import java.util.Locale;

public enum ImprovementPracticeStatus {
    PENDING,
    GENERATING,
    COMPLETED,
    FAILED;

    public String toEventValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
