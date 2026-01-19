package app.mockly.domain.product.dto.response;

import app.mockly.domain.product.dto.SubscriptionProductInfo;
import app.mockly.domain.product.entity.SubscriptionProduct;

import java.util.List;

public record GetSubscriptionProductsResponse(
        List<SubscriptionProductInfo> products
) {
    public static GetSubscriptionProductsResponse from(List<SubscriptionProduct> products, Integer activePlanId) {
        List<SubscriptionProductInfo> productInfos = products.stream()
                .map(product -> SubscriptionProductInfo.from(product, activePlanId))
                .toList();
        return new GetSubscriptionProductsResponse(productInfos);
    }
}
