package app.mockly.domain.plan.repository;

import app.mockly.domain.plan.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, Integer> {
    @Query("SELECT DISTINCT p " +
            "FROM Plan p " +
            "LEFT JOIN FETCH p.prices " +
            "WHERE p.isActive = true")
    List<Plan> findAllActiveWithPrices();
}
