package app.mockly.domain.product.service;

import app.mockly.domain.product.entity.Subscription;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionAccessService {

    private final CurrentSubscriptionService currentSubscriptionService;

    public void validateInterviewAccess(UUID userId) {
        validateInterviewAccess(userId, Instant.now());
    }

    void validateInterviewAccess(UUID userId, Instant now) {
        Subscription subscription = currentSubscriptionService.getCurrentSubscription(userId);
        if (subscription.isUnpaid() || subscription.isGracePeriodExpired(now)) {
            throw new BusinessException(ApiStatusCode.SUBSCRIPTION_UNPAID);
        }
    }
}
