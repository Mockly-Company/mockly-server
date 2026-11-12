package app.mockly.domain.meetup.repository;

import app.mockly.domain.meetup.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LocalInterviewMeetupRepository extends JpaRepository<LocalInterviewMeetup, UUID> {

    String HAVERSINE_H_EXPR = """
            (
              SIN((m.latitude - :originLat) * 0.008726646259971648)
                * SIN((m.latitude - :originLat) * 0.008726646259971648)
              + :cosOriginLat * COS(m.latitude * 0.017453292519943295)
                * SIN((m.longitude - :originLng) * 0.008726646259971648)
                * SIN((m.longitude - :originLng) * 0.008726646259971648)
            )
            """;

    String BASE_BOUNDING_BOX_WHERE = """
            WHERE m.startsAt > :now
              AND m.latitude BETWEEN :minLat AND :maxLat
              AND m.longitude BETWEEN :minLng AND :maxLng
              AND (:jobCategory IS NULL OR m.jobCategory = :jobCategory)
              AND (:jobRole IS NULL OR m.jobRole = :jobRole)
              AND (:interviewType IS NULL OR m.interviewType = :interviewType)
              AND (:experienceLevel IS NULL OR m.experienceLevel = :experienceLevel)
            """;

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

    @Query(
            value = "SELECT m FROM LocalInterviewMeetup m "
                    + BASE_BOUNDING_BOX_WHERE
                    + " ORDER BY m.startsAt ASC, m.id ASC",
            countQuery = "SELECT COUNT(m) FROM LocalInterviewMeetup m " + BASE_BOUNDING_BOX_WHERE
    )
    Page<LocalInterviewMeetup> searchInBounds(
            @Param("now") Instant now,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng,
            @Param("jobCategory") MeetupJobCategory jobCategory,
            @Param("jobRole") MeetupJobRole jobRole,
            @Param("interviewType") MeetupInterviewType interviewType,
            @Param("experienceLevel") MeetupExperienceLevel experienceLevel,
            Pageable pageable
    );

    @Query(
            value = "SELECT m FROM LocalInterviewMeetup m "
                    + BASE_BOUNDING_BOX_WHERE
                    + " AND " + HAVERSINE_H_EXPR + " <= :maxHaversineH "
                    + " ORDER BY " + HAVERSINE_H_EXPR + " ASC, m.startsAt ASC, m.id ASC",
            countQuery = "SELECT COUNT(m) FROM LocalInterviewMeetup m "
                    + BASE_BOUNDING_BOX_WHERE
                    + " AND " + HAVERSINE_H_EXPR + " <= :maxHaversineH"
    )
    Page<LocalInterviewMeetup> searchInRadiusOrderByDistance(
            @Param("now") Instant now,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng,
            @Param("originLat") double originLat,
            @Param("originLng") double originLng,
            @Param("cosOriginLat") double cosOriginLat,
            @Param("maxHaversineH") double maxHaversineH,
            @Param("jobCategory") MeetupJobCategory jobCategory,
            @Param("jobRole") MeetupJobRole jobRole,
            @Param("interviewType") MeetupInterviewType interviewType,
            @Param("experienceLevel") MeetupExperienceLevel experienceLevel,
            Pageable pageable
    );

    @Query(
            value = "SELECT m FROM LocalInterviewMeetup m "
                    + BASE_BOUNDING_BOX_WHERE
                    + " AND " + HAVERSINE_H_EXPR + " <= :maxHaversineH "
                    + " ORDER BY m.startsAt ASC, " + HAVERSINE_H_EXPR + " ASC, m.id ASC",
            countQuery = "SELECT COUNT(m) FROM LocalInterviewMeetup m "
                    + BASE_BOUNDING_BOX_WHERE
                    + " AND " + HAVERSINE_H_EXPR + " <= :maxHaversineH"
    )
    Page<LocalInterviewMeetup> searchInRadiusOrderByStartsAt(
            @Param("now") Instant now,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng,
            @Param("originLat") double originLat,
            @Param("originLng") double originLng,
            @Param("cosOriginLat") double cosOriginLat,
            @Param("maxHaversineH") double maxHaversineH,
            @Param("jobCategory") MeetupJobCategory jobCategory,
            @Param("jobRole") MeetupJobRole jobRole,
            @Param("interviewType") MeetupInterviewType interviewType,
            @Param("experienceLevel") MeetupExperienceLevel experienceLevel,
            Pageable pageable
    );
}
