package app.mockly.domain.interview.service;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.interview.dto.response.GetQuotaResponse;
import app.mockly.domain.interview.dto.WeeklyQuotaContext;
import app.mockly.domain.interview.entity.QuotaType;
import app.mockly.domain.interview.entity.QuotaUsage;
import app.mockly.domain.interview.repository.QuotaUsageRepository;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionProduct;
import app.mockly.domain.product.entity.SubscriptionStatus;
import app.mockly.domain.product.service.CurrentSubscriptionService;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeeklyQuotaService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CurrentSubscriptionService currentSubscriptionService;
    private final QuotaUsageRepository quotaUsageRepository;

    public GetQuotaResponse getQuota(User user) {
        WeeklyQuotaContext context = calculateCurrentQuotaContext(user, Instant.now());
        int interviewUsed = getUsed(user.getId(), QuotaType.INTERVIEW, context.periodStart());
        int improvementUsed = getUsed(user.getId(), QuotaType.IMPROVEMENT_PRACTICE, context.periodStart());

        return GetQuotaResponse.of(context, interviewUsed, improvementUsed);
    }

    public WeeklyQuotaContext calculateCurrentQuotaContext(User user, Instant now) {
        Subscription subscription = currentSubscriptionService.getCurrentSubscription(user.getId());
        SubscriptionProduct product = subscription.getSubscriptionPlan().getProduct();
        LocalDate quotaCycleAnchorDate = findQuotaCycleAnchorDate(subscription, user);
        LocalDate today = now.atZone(KST).toLocalDate();
        long elapsedDays = Math.max(0, ChronoUnit.DAYS.between(quotaCycleAnchorDate, today));
        LocalDate periodStart = quotaCycleAnchorDate.plusDays((elapsedDays / 7) * 7);

        boolean available = isAvailableForPeriod(subscription, periodStart);
        return WeeklyQuotaContext.of(product, periodStart, available);
    }

    public void consume(UUID userId, WeeklyQuotaContext context, QuotaType quotaType) {
        if (!context.available()) {
            throw new BusinessException(ApiStatusCode.QUOTA_EXCEEDED);
        }

        int limit = switch (quotaType) {
            case INTERVIEW -> context.product().getWeeklyInterviewLimit();
            case IMPROVEMENT_PRACTICE -> context.product().getWeeklyImprovementPracticeLimit();
        };
        QuotaUsage usage = quotaUsageRepository.findByUserIdAndQuotaTypeAndPeriodStart(userId, quotaType, context.periodStart())
                .orElseGet(() -> QuotaUsage.create(userId, quotaType, context.periodStart()));
        if (!usage.consumeIfAvailable(limit)) {
            throw new BusinessException(ApiStatusCode.QUOTA_EXCEEDED);
        }
        quotaUsageRepository.save(usage);
    }

    private int getUsed(UUID userId, QuotaType quotaType, LocalDate periodStart) {
        return quotaUsageRepository.findByUserIdAndQuotaTypeAndPeriodStart(userId, quotaType, periodStart)
                .map(QuotaUsage::getUsedCount)
                .orElse(0);
    }

    private LocalDate findQuotaCycleAnchorDate(Subscription subscription, User user) {
        return toKstDate(subscription.getCurrentPeriodStart(), user.getCreatedAt());
    }

    private boolean isAvailableForPeriod(Subscription subscription, LocalDate periodStart) {
        if (subscription.getStatus() != SubscriptionStatus.PAST_DUE) {
            return true;
        }

        return !periodStart.atStartOfDay(KST).toInstant().isAfter(subscription.getPastDueAt());
    }

    private LocalDate toKstDate(LocalDateTime periodStart, Instant fallback) {
        if (periodStart == null) {
            return fallback.atZone(KST).toLocalDate();
        }

        return periodStart.toLocalDate();
    }

}
