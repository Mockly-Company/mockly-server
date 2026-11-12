package app.mockly.domain.interview.repository;

import app.mockly.domain.interview.entity.ImprovementPractice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ImprovementPracticeRepository extends JpaRepository<ImprovementPractice, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ip FROM ImprovementPractice ip WHERE ip.id = :practiceId")
    Optional<ImprovementPractice> findByIdForUpdate(@Param("practiceId") Long practiceId);

    @Query("""
            SELECT ip FROM ImprovementPractice ip
                JOIN FETCH ip.improvement fi
                JOIN FETCH ip.session s
            WHERE ip.id = :practiceId
            """)
    Optional<ImprovementPractice> findByIdWithImprovementAndSession(@Param("practiceId") Long practiceId);
}
