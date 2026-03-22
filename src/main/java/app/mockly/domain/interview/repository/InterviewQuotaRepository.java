package app.mockly.domain.interview.repository;

import app.mockly.domain.interview.entity.InterviewQuota;
import app.mockly.domain.product.entity.PlanTier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewQuotaRepository extends JpaRepository<InterviewQuota, PlanTier> {
}
