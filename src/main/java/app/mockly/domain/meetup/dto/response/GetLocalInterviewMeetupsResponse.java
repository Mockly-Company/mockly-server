package app.mockly.domain.meetup.dto.response;

import app.mockly.domain.meetup.entity.*;

import java.time.Instant;
import java.util.List;

public record GetLocalInterviewMeetupsResponse(
        List<MeetupListItem> meetups,
        Pagination pagination
) {
    public record MeetupListItem(
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
            double longitude
    ) {
        public static MeetupListItem from(LocalInterviewMeetup meetup, Integer distanceMeters) {
            return new MeetupListItem(
                    meetup.getId().toString(),
                    meetup.getTitle(),
                    meetup.getJobCategory(),
                    meetup.getJobRole(),
                    meetup.getInterviewType(),
                    meetup.getExperienceLevel(),
                    meetup.getStartsAt(),
                    distanceMeters,
                    meetup.getCurrentParticipants(),
                    meetup.getMaxParticipants(),
                    meetup.getPlaceName(),
                    meetup.getAddressLabel(),
                    meetup.getLatitude(),
                    meetup.getLongitude()
            );
        }
    }

    public record Pagination(
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }
}
