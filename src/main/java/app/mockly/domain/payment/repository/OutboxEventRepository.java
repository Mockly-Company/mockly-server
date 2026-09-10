package app.mockly.domain.payment.repository;

import app.mockly.domain.payment.entity.OutboxEvent;
import app.mockly.domain.payment.entity.OutboxEventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("""
            SELECT event.id
            FROM OutboxEvent event
            WHERE event.status = :status
            ORDER BY event.createdAt ASC
            """)
    List<Long> findIdsByStatusOrderByCreatedAtAsc(
            @Param("status") OutboxEventStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT event FROM OutboxEvent event WHERE event.id = :eventId")
    Optional<OutboxEvent> findByIdForUpdate(@Param("eventId") Long eventId);
}
