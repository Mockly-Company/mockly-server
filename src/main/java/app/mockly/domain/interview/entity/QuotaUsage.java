package app.mockly.domain.interview.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quota_usage", uniqueConstraints = @UniqueConstraint(
        name = "uk_quota_usage_user_type_period",
        columnNames = {"user_id", "quota_type", "period_start"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QuotaUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "quota_type", nullable = false, length = 30)
    private QuotaType quotaType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    public static QuotaUsage create(UUID userId, QuotaType quotaType, LocalDate periodStart) {
        return new QuotaUsage(null, userId, quotaType, periodStart, 0);
    }

    public boolean consumeIfAvailable(int limit) {
        if (usedCount >= limit) {
            return false;
        }
        usedCount++;
        return true;
    }
}
