package app.mockly.domain.product.service;

import app.mockly.domain.product.dto.response.CancelSubscriptionResponse;
import app.mockly.domain.product.dto.response.CreateSubscriptionResponse;
import app.mockly.domain.product.dto.response.GetSubscriptionResponse;
import app.mockly.domain.product.entity.BillingCycle;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionPlan;
import app.mockly.domain.product.entity.SubscriptionStatus;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionProductRepository subscriptionProductRepository;

    @Transactional
    public CreateSubscriptionResponse createSubscription(UUID userId, Integer planId) {
        SubscriptionPlan subscriptionPlan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "플랜을 찾을 수 없습니다."));
        if (subscriptionPlan.isFree()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "무료 플랜은 SMS 인증 후 자동으로 부여됩니다.");
        }

        subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .ifPresent(currentPlan -> {
                    if (currentPlan.getSubscriptionPlan().isFree()) {
                        currentPlan.cancel();
                    } else {
                        throw new BusinessException(ApiStatusCode.BAD_REQUEST, "이미 구독중인 플랜이 있습니다. 구독 변경 API를 사용하세요.");
                    }
                });

        Subscription subscription = Subscription.create(userId, subscriptionPlan);

        // TODO: PortOne 결제 진행 시작
        subscription.activate(); // TODO: 추후 결제 연동 시 결제 성공 후 activate로 변경
        Subscription saved = subscriptionRepository.save(subscription);

        return CreateSubscriptionResponse.from(saved);
    }

    public GetSubscriptionResponse getMySubscription(UUID userId) {
        return subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .map(GetSubscriptionResponse::from)
                .orElse(null);
    }

    @Transactional
    public CancelSubscriptionResponse cancelSubscription(UUID userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "구독을 찾을 수 없습니다"));

        if (!subscription.getUserId().equals(userId)) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "본인의 구독만 해지할 수 있습니다.");
        }
        if (!subscription.isActive()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "이미 해지되었거나 비활성 상태입니다.");
        }

        subscription.cancel();
        subscriptionRepository.save(subscription);

        // TODO: PortOne 구독 예약 스케줄링 해지

        return CancelSubscriptionResponse.from(subscription);
    }

    public void assignFreePlan(UUID userId) {
        subscriptionPlanRepository.findByBillingCycle(BillingCycle.LIFETIME)
                .ifPresent(freePLan -> {
                    Subscription subscription = Subscription.create(userId, freePLan);
                    subscription.activate();
                    subscriptionRepository.save(subscription);
                });
    }
}
