package app.mockly.domain.product.scheduler;

import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionStatus;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.domain.product.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CanceledPeriodEndSubscriptionJob {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @Scheduled(fixedDelayString = "${subscription.lifecycle.check-interval-ms:60000}")
    public void processExpiredCancelAtPeriodEndSubscriptions() {
        processExpiredCancelAtPeriodEndSubscriptions(LocalDateTime.now());
    }

    public void processExpiredCancelAtPeriodEndSubscriptions(LocalDateTime now) {
        List<Subscription> expiredSubscriptions = subscriptionRepository
                .findExpiredCancelAtPeriodEndSubscriptions(SubscriptionStatus.ACTIVE, now);

        if (expiredSubscriptions.isEmpty()) {
            return;
        }

        log.info("해지 예약 만료 구독 정리 시작 - 대상 수: {}", expiredSubscriptions.size());

        for (Subscription subscription : expiredSubscriptions) {
            try {
                subscriptionService.completeExpiredCancelAtPeriodEndSubscription(subscription.getId(), now);
            } catch (Exception e) {
                log.error("해지 예약 만료 구독 Free 전환 실패 - subscriptionId: {}", subscription.getId(), e);
            }
        }
    }
}
