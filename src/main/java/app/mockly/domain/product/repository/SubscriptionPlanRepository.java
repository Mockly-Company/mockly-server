package app.mockly.domain.product.repository;

import app.mockly.domain.product.entity.BillingCycle;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Integer> {
    Optional<SubscriptionPlan> findByBillingCycle(BillingCycle billingCycle);

    @Query("""
            SELECT sp FROM SubscriptionPlan sp
                JOIN FETCH sp.product p
            WHERE p.planTier = :planTier
              AND p.isActive = true
              AND sp.billingCycle = :billingCycle
            """)
    Optional<SubscriptionPlan> findActiveByPlanTierAndBillingCycle(
            @Param("planTier") PlanTier planTier,
            @Param("billingCycle") BillingCycle billingCycle);
}
