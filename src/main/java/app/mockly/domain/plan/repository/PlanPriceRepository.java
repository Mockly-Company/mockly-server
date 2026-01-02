package app.mockly.domain.plan.repository;

import app.mockly.domain.plan.entity.PlanPrice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPriceRepository extends JpaRepository<PlanPrice, Integer> {

}
