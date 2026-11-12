package app.mockly.domain.meetup.dto.response;

import app.mockly.domain.meetup.entity.*;

import java.time.Instant;
import java.util.List;

public record GetMyLocalInterviewMeetupsResponse(
        List<MyMeetupItem> meetups
) {
    public record MyMeetupItem(
            String meetupId,
            String title,
            MeetupJobCategory jobCategory,
            MeetupJobRole jobRole,
            MeetupInterviewType interviewType,
            MeetupExperienceLevel experienceLevel,
            Instant startsAt,
            Integer distanceMeters,
            int currentParticipants,
            int maxParticipants,
            String placeName,
            String addressLabel,
            double latitude,
            double longitude,
            MeetupMemberRole role,
            MeetupParticipationStatus myParticipationStatus,
            int pendingApplicantCount,
            Instant endsAt
    ) {
        public static MyMeetupItem from(LocalInterviewMeetupParticipant participant, int pendingApplicantCount) {
            LocalInterviewMeetup meetup = participant.getMeetup();
            return new MyMeetupItem(
                    meetup.getId().toString(),
                    meetup.getTitle(),
                    meetup.getJobCategory(),
                    meetup.getJobRole(),
                    meetup.getInterviewType(),
                    meetup.getExperienceLevel(),
                    meetup.getStartsAt(),
                    null,
                    meetup.getCurrentParticipants(),
                    meetup.getMaxParticipants(),
                    meetup.getPlaceName(),
                    meetup.getAddressLabel(),
                    meetup.getLatitude(),
                    meetup.getLongitude(),
                    participant.isHost() ? MeetupMemberRole.HOST : MeetupMemberRole.PARTICIPANT,
                    participant.getStatus(),
                    pendingApplicantCount,
                    meetup.getEndsAt()
            );
        }
    }
}
