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

    public static MeetupExperienceLevel fromExperienceYears(int years) {
        if (years <= 0) {
            return ENTRY;
        }
        if (years <= 2) {
            return JUNIOR;
        }
        if (years <= 5) {
            return MID;
        }
        return SENIOR;
    }
}
