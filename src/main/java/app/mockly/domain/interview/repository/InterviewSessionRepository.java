package app.mockly.domain.interview.repository;

import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {

    @Query("SELECT COUNT(s) FROM InterviewSession s WHERE s.user.id = :userId AND s.createdAt >= :startOfDay AND s.createdAt < :endOfDay")
    long countTodaySessions(@Param("userId") UUID userId,
                            @Param("startOfDay") Instant startOfDay,
                            @Param("endOfDay") Instant endOfDay);

    Optional<InterviewSession> findByIdAndUserId(UUID id, UUID userId);

    Page<InterviewSession> findByUserIdAndStatus(UUID userId, InterviewSessionStatus status, Pageable pageable);

    Page<InterviewSession> findByUserId(UUID userId, Pageable pageable);

    List<InterviewSession> findByFeedbackStatusInAndUpdatedAtBefore(List<FeedbackStatus> statuses, Instant threshold);

}
