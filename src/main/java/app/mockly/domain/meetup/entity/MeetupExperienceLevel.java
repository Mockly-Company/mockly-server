package app.mockly.domain.meetup.entity;

public enum MeetupExperienceLevel {
    ENTRY,
    JUNIOR,
    MID,
    SENIOR;

    public int defaultExperienceYears() {
        return switch (this) {
            case ENTRY -> 0;
            case JUNIOR -> 1;
            case MID -> 3;
            case SENIOR -> 6;
        };
    }
}
