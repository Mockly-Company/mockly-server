package app.mockly.domain.meetup.dto.request;

import app.mockly.domain.meetup.entity.MeetupExperienceLevel;
import app.mockly.domain.meetup.entity.MeetupJobCategory;
import app.mockly.domain.meetup.entity.MeetupJobRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CreateMeetupParticipantRequest(
        @Size(max = 300, message = "신청 메시지는 300자 이하여야 합니다.")
        String message,

        MeetupJobCategory jobCategory,

        MeetupJobRole jobRole,

        @Min(value = 0, message = "경력 연차는 0 이상이어야 합니다.")
        @Max(value = 50, message = "경력 연차는 50 이하여야 합니다.")
        Integer experienceYears,

        MeetupExperienceLevel experienceLevel
) {
    public CreateMeetupParticipantRequest(String message) {
        this(message, null, null, null, null);
    }
}
