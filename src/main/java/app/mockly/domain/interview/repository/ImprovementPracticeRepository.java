package app.mockly.domain.interview.repository;

import app.mockly.domain.interview.entity.ImprovementPractice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ImprovementPracticeRepository extends JpaRepository<ImprovementPractice, Long> {

    @Query("""
            SELECT ip FROM ImprovementPractice ip
                JOIN FETCH ip.improvement fi
                JOIN FETCH ip.session s
            WHERE ip.id = :practiceId
            """)
    Optional<ImprovementPractice> findByIdWithImprovementAndSession(@Param("practiceId") Long practiceId);
}
