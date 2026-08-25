package app.mockly.domain.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.interview.dto.WeeklyQuotaContext;
import app.mockly.domain.interview.repository.QuotaUsageRepository;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionPlan;
import app.mockly.domain.product.entity.SubscriptionProduct;
import app.mockly.domain.product.entity.SubscriptionStatus;
import app.mockly.domain.product.service.CurrentSubscriptionService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyQuotaServiceTest {

    @Mock
    private CurrentSubscriptionService currentSubscriptionService;

    @Mock
    private QuotaUsageRepository quotaUsageRepository;

    @Mock
    private User user;

    @Mock
    private Subscription subscription;

    @Mock
    private SubscriptionPlan plan;

    @Mock
    private SubscriptionProduct product;

    @InjectMocks
    private WeeklyQuotaService weeklyQuotaService;

    @Test
    void calculateCurrentQuotaContext_doesNotIssueAWeekThatStartedAfterPastDueAt() {
        UUID userId = UUID.randomUUID();
        given(user.getId()).willReturn(userId);
        given(currentSubscriptionService.getCurrentSubscription(userId)).willReturn(subscription);
        given(subscription.getSubscriptionPlan()).willReturn(plan);
        given(plan.getProduct()).willReturn(product);
        given(subscription.getCurrentPeriodStart()).willReturn(LocalDateTime.of(2026, 8, 1, 12, 0));
        given(subscription.getStatus()).willReturn(SubscriptionStatus.PAST_DUE);
        given(subscription.getPastDueAt()).willReturn(Instant.parse("2026-08-05T03:00:00Z"));
        WeeklyQuotaContext context = weeklyQuotaService.calculateCurrentQuotaContext(
                user,
                Instant.parse("2026-08-12T03:00:00Z")
        );

        assertThat(context.available()).isFalse();
    }
}
