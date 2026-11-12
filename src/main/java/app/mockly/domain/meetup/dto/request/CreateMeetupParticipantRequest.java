package app.mockly.domain.meetup.dto.request;

import jakarta.validation.constraints.Size;

public record CreateMeetupParticipantRequest(
        @Size(max = 300, message = "신청 메시지는 300자 이하여야 합니다.")
        String message
) {
}
