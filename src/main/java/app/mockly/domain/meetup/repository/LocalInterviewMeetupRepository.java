package app.mockly.domain.meetup.repository;

import app.mockly.domain.meetup.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocalInterviewMeetupRepository extends JpaRepository<LocalInterviewMeetup, UUID> {

    @Query("""
            SELECT m
            FROM LocalInterviewMeetup m
            JOIN FETCH m.host
            WHERE m.id = :meetupId
            """)
    Optional<LocalInterviewMeetup> findByIdWithHost(@Param("meetupId") UUID meetupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m
            FROM LocalInterviewMeetup m
            JOIN FETCH m.host
            WHERE m.id = :meetupId
            """)
    Optional<LocalInterviewMeetup> findByIdForUpdate(@Param("meetupId") UUID meetupId);

    @Query("""
            SELECT m
            FROM LocalInterviewMeetup m
            WHERE m.startsAt > :now
              AND m.latitude BETWEEN :minLat AND :maxLat
              AND m.longitude BETWEEN :minLng AND :maxLng
              AND (:jobCategory IS NULL OR m.jobCategory = :jobCategory)
              AND (:jobRole IS NULL OR m.jobRole = :jobRole)
              AND (:interviewType IS NULL OR m.interviewType = :interviewType)
              AND (:experienceLevel IS NULL OR m.experienceLevel = :experienceLevel)
            ORDER BY m.startsAt ASC
            """)
    List<LocalInterviewMeetup> findInBoundingBox(
            @Param("now") Instant now,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng,
            @Param("jobCategory") MeetupJobCategory jobCategory,
            @Param("jobRole") MeetupJobRole jobRole,
            @Param("interviewType") MeetupInterviewType interviewType,
            @Param("experienceLevel") MeetupExperienceLevel experienceLevel
    );
}
