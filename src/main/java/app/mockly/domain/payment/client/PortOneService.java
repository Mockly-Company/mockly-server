package app.mockly.domain.payment.client;

import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.config.PortOneProperties;
import app.mockly.global.exception.BusinessException;
import io.portone.sdk.server.PortOneClient;
import io.portone.sdk.server.common.Currency;
import io.portone.sdk.server.common.PaymentAmountInput;
import io.portone.sdk.server.payment.PayWithBillingKeyResponse;
import io.portone.sdk.server.payment.billingkey.BillingKeyInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneService {
    private final PortOneClient portOneClient;
    private final PortOneProperties portOneProperties;

    /**
     * 빌링키 조회 및 검증
     */
    public BillingKeyInfo getBillingKey(String billingKey) {
        log.info("PortOne Billing Key 조회: {}", billingKey);

        try {
            return portOneClient.getPayment().getBillingKey()
                    .getBillingKeyInfo(billingKey)
                    .join();
        } catch (Exception e) {
            log.error("빌링키 조회 실패: {}", billingKey, e);
            // TODO: 다른 Exception으로 만들 필요가 있는지 확인 필요
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "유효하지 않은 빌링키입니다.");
        }
    }

    /**
     * 빌링키로 결제 처리
     */
    public PayWithBillingKeyResponse payWithBillingKey(
            String paymentId,
            String billingKey,
            String orderName,
            Currency currency,
            PaymentAmountInput amount
    ) {
        log.info("Billing Key 결제 시작 - paymentId: {}, billingKey: {}", paymentId, billingKey);

        try {
            return portOneClient.getPayment().payWithBillingKey(
                    paymentId,
                    billingKey,
                    portOneProperties.channelKey(),
                    orderName,
                    null,
                    null,
                    amount,
                    currency,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null).join();
        } catch (Exception e) {
            log.error("빌링키 결제 실패 - paymentId: {}", paymentId, e);
            // TODO: 다른 Exception으로 만들 필요가 있는지 확인 필요
            throw new BusinessException(ApiStatusCode.INTERNAL_SERVER_ERROR, "결제 처리 중 오류가 발생했습니다");
        }

    }
}
