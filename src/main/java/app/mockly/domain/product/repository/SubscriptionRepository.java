package app.mockly.domain.product.repository;

import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    boolean existsByUserIdAndStatus(UUID userId, SubscriptionStatus status);

    @Query("""
            SELECT s FROM Subscription s
                JOIN FETCH s.subscriptionPlan sp
                JOIN FETCH sp.product p
                LEFT JOIN FETCH s.scheduledPlan ssp
                LEFT JOIN FETCH ssp.product
                LEFT JOIN FETCH s.paymentMethod
            WHERE s.userId = :userId AND s.status = :status
            """)
    Optional<Subscription> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") SubscriptionStatus status);

    @Query("""
            SELECT s FROM Subscription s
                JOIN FETCH s.subscriptionPlan sp
                JOIN FETCH sp.product p
                LEFT JOIN FETCH s.scheduledPlan ssp
                LEFT JOIN FETCH ssp.product
                LEFT JOIN FETCH s.paymentMethod
            WHERE s.userId = :userId AND s.currentMarker = true
            """)
    Optional<Subscription> findCurrentByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT s FROM Subscription s
                JOIN FETCH s.subscriptionPlan sp
                JOIN FETCH sp.product p
                LEFT JOIN FETCH s.scheduledPlan ssp
                LEFT JOIN FETCH ssp.product
                LEFT JOIN FETCH s.paymentMethod
            WHERE s.id = :id
            """)
    Optional<Subscription> findByIdWithDetails(@Param("id") Long id);

    List<Subscription> findByStatusAndPastDueAtLessThanEqual(SubscriptionStatus status, Instant cutoff);

    @Query("""
            SELECT s FROM Subscription s
                JOIN FETCH s.subscriptionPlan sp
                JOIN FETCH sp.product p
            WHERE s.status = :status
              AND s.cancelAtPeriodEnd = true
              AND s.currentPeriodEnd IS NOT NULL
              AND s.currentPeriodEnd <= :cutoff
            """)
    List<Subscription> findExpiredCancelAtPeriodEndSubscriptions(
            @Param("status") SubscriptionStatus status,
            @Param("cutoff") LocalDateTime cutoff
    );

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
