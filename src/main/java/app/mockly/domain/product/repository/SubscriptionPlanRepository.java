package app.mockly.domain.product.repository;

import app.mockly.domain.product.entity.BillingCycle;
import app.mockly.domain.product.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Integer> {
    Optional<SubscriptionPlan> findByBillingCycle(BillingCycle billingCycle);
}
