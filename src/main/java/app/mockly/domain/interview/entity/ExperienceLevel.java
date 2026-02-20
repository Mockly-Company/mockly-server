package app.mockly.domain.interview.entity;

public enum ExperienceLevel {
    NEW_GRAD,
    JUNIOR,
    MID,
    SENIOR;

    public String getDescription() {
        return switch (this) {
            case NEW_GRAD -> "신입";
            case JUNIOR -> "주니어 (1~3년)";
            case MID -> "미드레벨 (4~7년)";
            case SENIOR -> "시니어 (8년 이상)";
        };
    }
}
