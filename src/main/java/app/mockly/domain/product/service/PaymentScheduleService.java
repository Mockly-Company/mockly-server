package app.mockly.domain.product.service;

import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.entity.Invoice;
import app.mockly.domain.payment.entity.Payment;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.InvoiceRepository;
import app.mockly.domain.payment.repository.PaymentRepository;
import app.mockly.domain.product.entity.BillingCycle;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import io.portone.sdk.server.common.PaymentAmountInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentScheduleService {
    private final PortOneService portOneService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    public String createSchedule(
            Subscription subscription,
            String billingKey,
            LocalDateTime periodStart,
            LocalDateTime periodEnd
    ) {
        Invoice invoice = Invoice.create(
                subscription,
                subscription.getSubscriptionPlan().getPrice(),
                subscription.getSubscriptionPlan().getCurrency(),
                periodStart,
                periodEnd
        );
        invoiceRepository.save(invoice);

        Payment payment = Payment.create(
                invoice,
                subscription.getSubscriptionPlan().getPrice(),
                subscription.getSubscriptionPlan().getCurrency()
        );
        paymentRepository.save(payment);

        java.time.Instant timeToPay = periodStart
                .atZone(java.time.ZoneId.of("UTC"))
                .toInstant();
        String scheduleId = portOneService.createPaymentSchedule(
                payment.getId(),
                billingKey,
                subscription.getSubscriptionPlan().getProduct().getName() + " - 갱신",
                subscription.getSubscriptionPlan().getCurrency().toPortOneCurrency(),
                new PaymentAmountInput(subscription.getSubscriptionPlan().getPrice().longValue(), null, null),
                timeToPay
        );

        subscription.setCurrentPaymentScheduleId(scheduleId);
        return scheduleId;
    }

    public void replaceSchedule(Subscription subscription, PaymentMethod newPaymentMethod) {
        String oldScheduleId = subscription.getCurrentPaymentScheduleId();
        if (oldScheduleId != null) {
            try {
                portOneService.revokePaymentSchedule(oldScheduleId);
                log.info("결제 수단 변경: 기존 스케줄 취소 완료 - subscriptionId: {}, oldScheduleId: {}",
                        subscription.getId(), oldScheduleId);
            } catch (Exception e) {
                log.error("결제 수단 변경: 기존 스케줄 취소 실패 - subscriptionId: {}, oldScheduleId: {}",
                        subscription.getId(), oldScheduleId, e);
                throw new BusinessException(ApiStatusCode.INTERNAL_SERVER_ERROR,
                        "기존 결제 스케줄 취소 중 오류가 발생했습니다.");
            }
        }

        try {
            LocalDateTime nextPeriodStart = subscription.getCurrentPeriodEnd();
            LocalDateTime nextPeriodEnd = calculateNextPeriodEnd(
                    nextPeriodStart,
                    subscription.getSubscriptionPlan().getBillingCycle()
            );

            String newScheduleId = createSchedule(
                    subscription,
                    newPaymentMethod.getBillingKey(),
                    nextPeriodStart,
                    nextPeriodEnd
            );

            log.info("결제 수단 변경 완료 - subscriptionId: {}, newPaymentMethodId: {}, newScheduleId: {}",
                    subscription.getId(), newPaymentMethod.getId(), newScheduleId);
        } catch (Exception e) {
            log.error("결제 수단 변경: 새 스케줄 생성 실패 - subscriptionId: {}, newPaymentMethodId: {}",
                    subscription.getId(), newPaymentMethod.getId(), e);
            throw new BusinessException(ApiStatusCode.INTERNAL_SERVER_ERROR,
                    "새 결제 스케줄 생성 중 오류가 발생했습니다.");
        }
    }

    private LocalDateTime calculateNextPeriodEnd(LocalDateTime start, BillingCycle billingCycle) {
        return switch (billingCycle) {
            case MONTHLY -> start.plusMonths(1);
            case YEARLY -> start.plusYears(1);
            case LIFETIME -> null;
        };
    }
}
