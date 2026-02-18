package app.mockly.domain.payment.repository;

import app.mockly.domain.payment.entity.OutboxEvent;
import app.mockly.domain.payment.entity.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop20ByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

    Optional<OutboxEvent> findByAggregateIdAndEventTypeAndStatus(Long aggregateId, String eventType, OutboxEventStatus status);
}
