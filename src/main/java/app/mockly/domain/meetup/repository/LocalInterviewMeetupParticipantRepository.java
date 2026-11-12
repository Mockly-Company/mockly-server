package app.mockly.domain.meetup.repository;

import app.mockly.domain.meetup.entity.LocalInterviewMeetupParticipant;
import app.mockly.domain.meetup.entity.MeetupParticipationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocalInterviewMeetupParticipantRepository extends JpaRepository<LocalInterviewMeetupParticipant, Long> {

    @Query("""
            SELECT p
            FROM LocalInterviewMeetupParticipant p
            JOIN FETCH p.user
            WHERE p.meetup.id = :meetupId
              AND p.status = :status
            ORDER BY p.host DESC, p.appliedAt ASC
            """)
    List<LocalInterviewMeetupParticipant> findAllByMeetupIdAndStatusWithUser(
            @Param("meetupId") UUID meetupId,
            @Param("status") MeetupParticipationStatus status
    );

    @Query("""
            SELECT p
            FROM LocalInterviewMeetupParticipant p
            JOIN FETCH p.user
            WHERE p.meetup.id = :meetupId
            ORDER BY p.host DESC, p.appliedAt ASC
            """)
    List<LocalInterviewMeetupParticipant> findAllByMeetupIdWithUser(@Param("meetupId") UUID meetupId);

    Optional<LocalInterviewMeetupParticipant> findByMeetupIdAndUserId(UUID meetupId, UUID userId);

    @Query("""
            SELECT p
            FROM LocalInterviewMeetupParticipant p
            JOIN FETCH p.meetup m
            WHERE p.user.id = :userId
            ORDER BY m.startsAt ASC
            """)
    List<LocalInterviewMeetupParticipant> findAllByUserIdWithMeetup(@Param("userId") UUID userId);

    @Query("""
            SELECT p.meetup.id AS meetupId, COUNT(p) AS cnt
            FROM LocalInterviewMeetupParticipant p
            WHERE p.meetup.id IN :meetupIds
              AND p.status = :status
            GROUP BY p.meetup.id
            """)
    List<MeetupPendingCountProjection> countByMeetupIdsAndStatus(
            @Param("meetupIds") Collection<UUID> meetupIds,
            @Param("status") MeetupParticipationStatus status
    );

    interface MeetupPendingCountProjection {
        UUID getMeetupId();

        long getCnt();
    }
}
