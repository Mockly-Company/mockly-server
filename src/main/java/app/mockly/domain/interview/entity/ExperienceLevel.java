package app.mockly.domain.interview.entity;

public enum ExperienceLevel {
    JUNIOR,
    MID,
    SENIOR;

    public String getDescription() {
        return switch (this) {
            case JUNIOR -> "주니어 (0~2년)";
            case MID -> "미드레벨 (2~5년)";
            case SENIOR -> "시니어 (5년 이상)";
        };
    }
}
