package app.mockly.domain.plan.dto;

import app.mockly.domain.plan.entity.BillingCycle;
import app.mockly.domain.plan.entity.Currency;
import app.mockly.domain.plan.entity.Plan;
import app.mockly.domain.plan.entity.PlanPrice;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;

import java.math.BigDecimal;
import java.util.List;

public record PlanInfo(
        Integer id,
        String name,
        String description,
        BigDecimal price,
        Currency currency,
        List<String> features,
        BillingCycle billingCycle,
        boolean isActive // 사용자 구독 여부
) {

    public static PlanInfo from(Plan plan, boolean isActive) {
        BigDecimal price = plan.getPrices().stream()
                .filter(planPrice -> planPrice.getCurrency().equals(Currency.KRW))
                .findFirst()
                .map(PlanPrice::getPrice)
                .orElseThrow(() -> new BusinessException(
                        ApiStatusCode.RESOURCE_NOT_FOUND,
                        "플랜 " + plan.getName() + "에 대한 KRW 가격이 없습니다."));

        return new PlanInfo(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                price,
                Currency.KRW,
                plan.getFeatures(),
                plan.getBillingCycle(),
                isActive // TODO: 사용자 구독 여부 확인 로직 변경
        );
    }
}
