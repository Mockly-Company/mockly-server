package app.mockly.domain.product.repository;

import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    boolean existsByUserIdAndStatus(UUID userId, SubscriptionStatus status);

    @Query("""
            SELECT s FROM Subscription s
                JOIN FETCH s.subscriptionPlan sp
                JOIN FETCH sp.product p
            WHERE s.userId = :userId AND s.status = :status
            """)
    Optional<Subscription> findByUserIdAndStatus(UUID userId, SubscriptionStatus status);

    @Query("""
            SELECT s FROM Subscription s
                JOIN FETCH s.subscriptionPlan sp
            WHERE s.userId = :userId
                AND sp.id = :planId
                AND s.status = :status
            """)
    Optional<Subscription> findByUserIdAndPlanIdAndStatus(UUID userId, Integer planId, SubscriptionStatus status);

    List<Subscription> findByStatusAndUpdatedAtBefore(SubscriptionStatus status, Instant cutoff);
}
