package app.mockly.domain.meetup.dto;

import app.mockly.domain.meetup.entity.*;

public record MeetupSearchCriteria(
        Double latitude,
        Double longitude,
        Integer radiusMeters,
        Double swLat,
        Double swLng,
        Double neLat,
        Double neLng,
        MeetupJobCategory jobCategory,
        MeetupJobRole jobRole,
        MeetupInterviewType interviewType,
        MeetupExperienceLevel experienceLevel,
        MeetupSortType sort,
        int page,
        int size
) {
}
