package app.mockly.domain.product.service;

import app.mockly.domain.product.dto.response.GetSubscriptionProductsResponse;
import app.mockly.domain.product.entity.SubscriptionProduct;
import app.mockly.domain.product.entity.SubscriptionStatus;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionProductService {
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionProductRepository subscriptionProductRepository;

    public GetSubscriptionProductsResponse getAllSubscriptionProducts(UUID userId) {
        List<SubscriptionProduct> subscriptionProducts = subscriptionProductRepository.findAllActiveWithPlans();

        Integer activePlanId = null;
        if (userId != null) {
            activePlanId = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                    .map(subscription -> subscription.getSubscriptionPlan().getId())
                    .orElse(null);
        }

        return GetSubscriptionProductsResponse.from(subscriptionProducts, activePlanId);
    }
}
