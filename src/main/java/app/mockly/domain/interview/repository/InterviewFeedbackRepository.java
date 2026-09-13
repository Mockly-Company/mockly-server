package app.mockly.domain.interview.repository;

import app.mockly.domain.interview.entity.InterviewFeedback;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {

    Optional<InterviewFeedback> findBySessionId(UUID sessionId);

    @Query("""
            SELECT f.overallScore
            FROM InterviewFeedback f
            WHERE f.session.user.id = :userId
            ORDER BY f.session.endedAt DESC, f.session.id DESC
            """)
    List<Integer> findRecentOverallScores(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT f.nextPracticePoint
            FROM InterviewFeedback f
            WHERE f.session.user.id = :userId
              AND f.nextPracticePoint IS NOT NULL
            ORDER BY f.session.endedAt DESC, f.session.id DESC
            """)
    List<String> findRecentPracticePoints(@Param("userId") UUID userId, Pageable pageable);
}
