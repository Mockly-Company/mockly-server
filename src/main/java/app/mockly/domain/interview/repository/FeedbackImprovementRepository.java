package app.mockly.domain.interview.repository;

import app.mockly.domain.interview.entity.FeedbackImprovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FeedbackImprovementRepository extends JpaRepository<FeedbackImprovement, Long> {

    @Query("""
            SELECT fi FROM FeedbackImprovement fi
                JOIN FETCH fi.feedback f
                JOIN FETCH f.session s
                JOIN FETCH s.user u
            WHERE fi.id = :improvementId
            """)
    Optional<FeedbackImprovement> findByIdWithFeedbackAndSession(@Param("improvementId") Long improvementId);
}
