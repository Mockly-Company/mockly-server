package app.mockly.domain.meetup.entity;

public enum MeetupParticipationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isActive() {
        return this == PENDING || this == APPROVED;
    }
}
