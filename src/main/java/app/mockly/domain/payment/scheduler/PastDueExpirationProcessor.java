package app.mockly.domain.payment.scheduler;

import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionStatus;
import app.mockly.domain.product.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PastDueExpirationProcessor {
    private static final int GRACE_PERIOD_DAYS = 7;

    private final SubscriptionRepository subscriptionRepository;
    private final PortOneService portOneService;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void processExpiredPastDueSubscriptions() {
        Instant cutoff = Instant.now().minus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
        List<Subscription> expiredSubscriptions = subscriptionRepository
                .findByStatusAndUpdatedAtBefore(SubscriptionStatus.PAST_DUE, cutoff);

        if (expiredSubscriptions.isEmpty()) {
            return;
        }

        log.info("PAST_DUE 만료 처리 시작 - 대상 수: {}", expiredSubscriptions.size());

        for (Subscription subscription : expiredSubscriptions) {
            try {
                revokeScheduleIfExists(subscription);
                subscription.expire();
                log.info("구독 만료 처리 완료 - subscriptionId: {}", subscription.getId());
            } catch (Exception e) {
                log.error("구독 만료 처리 실패 - subscriptionId: {}", subscription.getId(), e);
            }
        }
    }

    private void revokeScheduleIfExists(Subscription subscription) {
        String scheduleId = subscription.getCurrentPaymentScheduleId();
        if (scheduleId != null) {
            portOneService.revokePaymentSchedule(scheduleId);
            subscription.setCurrentPaymentScheduleId(null);
        }
    }
}
