package app.mockly.domain.meetup.dto.response;

import app.mockly.domain.meetup.entity.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record GetMeetupApplicantsResponse(
        List<MeetupApplicantItem> participants
) {
    public record MeetupApplicantItem(
            String userId,
            String nickname,
            @JsonProperty("isHost") boolean isHost,
            MeetupJobCategory jobCategory,
            MeetupJobRole jobRole,
            int experienceYears,
            MeetupExperienceLevel experienceLevel,
            MeetupParticipationStatus status,
            String message,
            Instant appliedAt
    ) {
        public static MeetupApplicantItem from(LocalInterviewMeetupParticipant participant) {
            return new MeetupApplicantItem(
                    participant.getUser().getId().toString(),
                    participant.getUser().getName(),
                    participant.isHost(),
                    participant.getJobCategory(),
                    participant.getJobRole(),
                    participant.getExperienceYears(),
                    participant.getExperienceLevel(),
                    participant.getStatus(),
                    participant.getMessage(),
                    participant.getAppliedAt()
            );
        }
    }
}
