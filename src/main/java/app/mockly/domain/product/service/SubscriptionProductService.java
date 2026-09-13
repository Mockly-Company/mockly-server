package app.mockly.domain.product.service;

import app.mockly.domain.product.dto.response.GetSubscriptionProductsResponse;
import app.mockly.domain.product.entity.SubscriptionProduct;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionProductService {
    private final CurrentSubscriptionService currentSubscriptionService;
    private final SubscriptionProductRepository subscriptionProductRepository;

    public GetSubscriptionProductsResponse getAllSubscriptionProducts(UUID userId) {
        List<SubscriptionProduct> subscriptionProducts = subscriptionProductRepository.findAllActiveWithPlans();

        Integer activePlanId = null;
        if (userId != null) {
            activePlanId = currentSubscriptionService.findCurrentSubscription(userId)
                    .filter(subscription -> subscription.isActive() || subscription.isPastDue())
                    .map(subscription -> subscription.getSubscriptionPlan().getId())
                    .orElse(null);
        }

        return GetSubscriptionProductsResponse.from(subscriptionProducts, activePlanId);
    }
}
