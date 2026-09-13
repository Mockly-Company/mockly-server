package app.mockly.domain.interview.repository;

import app.mockly.domain.interview.entity.QuotaType;
import app.mockly.domain.interview.entity.QuotaUsage;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotaUsageRepository extends JpaRepository<QuotaUsage, Long> {
    Optional<QuotaUsage> findByUserIdAndQuotaTypeAndPeriodStart(
            UUID userId, QuotaType quotaType, LocalDate periodStart);
}
