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
                JOIN FETCH sp.product p
            WHERE s.userId = :userId AND s.currentMarker = true
            """)
    Optional<Subscription> findCurrentByUserId(UUID userId);

    List<Subscription> findByStatusAndPastDueAtLessThanEqual(SubscriptionStatus status, Instant cutoff);

    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
            FROM Subscription s
                JOIN s.subscriptionPlan sp
                JOIN sp.product p
            WHERE s.userId = :userId
              AND p.planTier <> app.mockly.domain.product.entity.PlanTier.FREE
              AND s.activatedAt IS NOT NULL
              AND s.activatedAt >= :feedbackGenerationStartedAt
            """)
    boolean existsActivatedPaidSubscriptionAfter(UUID userId, Instant feedbackGenerationStartedAt);
}
