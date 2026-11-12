package app.mockly.domain.meetup.dto.request;

import app.mockly.domain.meetup.entity.MeetupParticipationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateMeetupParticipantStatusRequest(
        @NotNull(message = "심사 상태(status)는 필수입니다.")
        MeetupParticipationStatus status
) {
}
