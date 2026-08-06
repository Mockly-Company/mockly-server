package app.mockly.domain.payment.scheduler;

import app.mockly.domain.payment.dto.outbox.CreateSchedulePayload;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.domain.product.service.SubscriptionService;
import app.mockly.domain.payment.entity.OutboxEvent;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduleCreationHandler {

    private final PaymentMethodRepository paymentMethodRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    public void handle(OutboxEvent event) {
        CreateSchedulePayload payload = deserializePayload(event.getPayload());
        if (payload.paymentMethodId() == null) {
            throw new NonRetryableOutboxException("INVALID_PAYLOAD", "paymentMethodId가 누락됐습니다.");
        }

        Subscription subscription = subscriptionRepository.findById(event.getAggregateId())
                .orElseThrow(() -> new NonRetryableOutboxException(
                        "SUBSCRIPTION_NOT_FOUND",
                        "구독을 찾을 수 없습니다."
                ));
        if (subscription.getCurrentPaymentScheduleId() != null) {
            return;
        }

        PaymentMethod paymentMethod = paymentMethodRepository.findById(payload.paymentMethodId())
                .orElseThrow(() -> new NonRetryableOutboxException(
                        "PAYMENT_METHOD_NOT_FOUND",
                        "결제 수단을 찾을 수 없습니다."
                ));
        if (!paymentMethod.getUser().getId().equals(subscription.getUserId())) {
            throw new NonRetryableOutboxException(
                    "PAYMENT_METHOD_OWNER_MISMATCH",
                    "구독 소유자의 결제 수단이 아닙니다."
            );
        }
        if (!paymentMethod.isActive()) {
            throw new NonRetryableOutboxException(
                    "PAYMENT_METHOD_INACTIVE",
                    "비활성화된 결제 수단입니다."
            );
        }

        try {
            subscriptionService.createFirstPaymentSchedule(subscription, paymentMethod.getBillingKey());
        } catch (BusinessException e) {
            if (e.getStatusCode() == ApiStatusCode.DUPLICATE_RESOURCE) {
                throw new NonRetryableOutboxException(
                        "PAYMENT_SCHEDULE_ALREADY_EXISTS",
                        "PortOne에 결제 스케줄이 이미 존재합니다.",
                        e
                );
            }
            throw e;
        }
    }

    private CreateSchedulePayload deserializePayload(String payload) {
        try {
            return objectMapper.readValue(payload, CreateSchedulePayload.class);
        } catch (JsonProcessingException e) {
            throw new NonRetryableOutboxException("INVALID_PAYLOAD", "Outbox payload 역직렬화 실패", e);
        }
    }
}
