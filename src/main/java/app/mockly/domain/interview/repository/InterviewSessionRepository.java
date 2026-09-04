package app.mockly.domain.interview.repository;

import app.mockly.domain.interview.entity.FeedbackStatus;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {

    Optional<InterviewSession> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = "feedback")
    Page<InterviewSession> findByUserIdAndStatus(UUID userId, InterviewSessionStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "feedback")
    Page<InterviewSession> findByUserId(UUID userId, Pageable pageable);

    @Query("""
            SELECT s
            FROM InterviewSession s
            WHERE s.user.id = :userId
              AND s.status IN (
                  app.mockly.domain.interview.entity.InterviewSessionStatus.FEEDBACK_PENDING,
                  app.mockly.domain.interview.entity.InterviewSessionStatus.COMPLETED
              )
              AND s.endedAt >= :periodStart
              AND s.endedAt < :periodEnd
            """)
    List<InterviewSession> findLastAnswerSubmittedSessionsInPeriod(@Param("userId") UUID userId,
                                                                   @Param("periodStart") Instant periodStart,
                                                                   @Param("periodEnd") Instant periodEnd);

    @EntityGraph(attributePaths = "feedback")
    List<InterviewSession> findTop3ByUserIdAndEndedAtIsNotNullOrderByEndedAtDesc(UUID userId);

    List<InterviewSession> findTop100ByFeedbackStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            List<FeedbackStatus> statuses,
            Instant threshold);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InterviewSession s
            SET s.feedbackStatus = :generatingStatus,
                s.feedbackGenerationTaskId = :taskId,
                s.updatedAt = :updatedAt
            WHERE s.id = :sessionId
              AND s.feedbackStatus = :pendingStatus
            """)
    int markFeedbackGeneratingIfPending(@Param("sessionId") UUID sessionId,
                                        @Param("pendingStatus") FeedbackStatus pendingStatus,
                                        @Param("generatingStatus") FeedbackStatus generatingStatus,
                                        @Param("taskId") UUID taskId,
                                        @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InterviewSession s
            SET s.status = :completedSessionStatus,
                s.feedbackStatus = :completedFeedbackStatus,
                s.feedbackGenerationTaskId = null,
                s.completedAt = :completedAt,
                s.updatedAt = :updatedAt
            WHERE s.id = :sessionId
              AND s.feedbackStatus = :generatingStatus
              AND s.feedbackGenerationTaskId = :taskId
            """)
    int completeFeedbackIfOwned(@Param("sessionId") UUID sessionId,
                                @Param("taskId") UUID taskId,
                                @Param("generatingStatus") FeedbackStatus generatingStatus,
                                @Param("completedFeedbackStatus") FeedbackStatus completedFeedbackStatus,
                                @Param("completedSessionStatus") InterviewSessionStatus completedSessionStatus,
                                @Param("completedAt") Instant completedAt,
                                @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InterviewSession s
            SET s.feedbackStatus = :failedStatus,
                s.feedbackGenerationTaskId = null,
                s.failReason = :failReason,
                s.updatedAt = :updatedAt
            WHERE s.id = :sessionId
              AND s.feedbackStatus = :generatingStatus
              AND s.feedbackGenerationTaskId = :taskId
            """)
    int failFeedbackIfOwned(@Param("sessionId") UUID sessionId,
                            @Param("taskId") UUID taskId,
                            @Param("generatingStatus") FeedbackStatus generatingStatus,
                            @Param("failedStatus") FeedbackStatus failedStatus,
                            @Param("failReason") String failReason,
                            @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InterviewSession s
            SET s.feedbackStatus = :pendingStatus,
                s.feedbackGenerationTaskId = null,
                s.failReason = null,
                s.updatedAt = :updatedAt
            WHERE s.id = :sessionId
              AND s.feedbackStatus = :generatingStatus
              AND s.feedbackGenerationTaskId = :taskId
              AND s.updatedAt < :threshold
            """)
    int requeueStaleGenerating(@Param("sessionId") UUID sessionId,
                               @Param("taskId") UUID taskId,
                               @Param("generatingStatus") FeedbackStatus generatingStatus,
                               @Param("pendingStatus") FeedbackStatus pendingStatus,
                               @Param("threshold") Instant threshold,
                               @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InterviewSession s
            SET s.feedbackGenerationTaskId = null,
                s.failReason = null,
                s.updatedAt = :updatedAt
            WHERE s.id = :sessionId
              AND s.feedbackStatus = :pendingStatus
              AND s.updatedAt < :threshold
            """)
    int requeueStalePending(@Param("sessionId") UUID sessionId,
                            @Param("pendingStatus") FeedbackStatus pendingStatus,
                            @Param("threshold") Instant threshold,
                            @Param("updatedAt") Instant updatedAt);

}
