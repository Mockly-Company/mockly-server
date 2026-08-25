package app.mockly.domain.product.service;

import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import java.util.UUID;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentSubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    public Subscription getCurrentSubscription(UUID userId) {
        return findCurrentSubscription(userId)
                .filter(this::isVisibleCurrentStatus)
                .orElseThrow(() -> new BusinessException(
                        ApiStatusCode.RESOURCE_NOT_FOUND,
                        "현재 구독을 찾을 수 없습니다."
                ));
    }

    public Optional<Subscription> findCurrentSubscription(UUID userId) {
        return subscriptionRepository.findCurrentByUserId(userId);
    }

    private boolean isVisibleCurrentStatus(Subscription subscription) {
        return subscription.isActive() || subscription.isPastDue() || subscription.isUnpaid();
    }
}
