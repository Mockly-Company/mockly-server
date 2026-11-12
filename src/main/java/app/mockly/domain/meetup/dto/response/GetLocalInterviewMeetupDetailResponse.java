package app.mockly.domain.meetup.dto.response;

import app.mockly.domain.meetup.entity.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record GetLocalInterviewMeetupDetailResponse(
        String meetupId,
        String title,
        String description,
        MeetupJobCategory jobCategory,
        MeetupJobRole jobRole,
        MeetupInterviewType interviewType,
        MeetupExperienceLevel experienceLevel,
        Instant startsAt,
        Instant endsAt,
        int currentParticipants,
        int maxParticipants,
        String placeName,
        String fullAddress,
        Integer distanceMeters,
        double latitude,
        double longitude,
        List<ParticipantSummary> participantSummaries,
        @JsonProperty("isFull") boolean isFull,
        Instant createdAt,
        Instant recruitDeadline,
        MeetupParticipationStatus myParticipationStatus,
        boolean amIHost
) {
    public record ParticipantSummary(
            String nickname,
            @JsonProperty("isHost") boolean isHost,
            MeetupJobCategory jobCategory,
            MeetupJobRole jobRole,
            int experienceYears,
            MeetupExperienceLevel experienceLevel
    ) {
        public static ParticipantSummary from(LocalInterviewMeetupParticipant participant) {
            return new ParticipantSummary(
                    participant.getUser().getName(),
                    participant.isHost(),
                    participant.getJobCategory(),
                    participant.getJobRole(),
                    participant.getExperienceYears(),
                    participant.getExperienceLevel()
            );
        }
    }
}
