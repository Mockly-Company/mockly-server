package app.mockly.domain.product.service;

import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.entity.Invoice;
import app.mockly.domain.payment.entity.Payment;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.InvoiceRepository;
import app.mockly.domain.payment.repository.PaymentRepository;
import app.mockly.domain.product.entity.BillingCycle;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionPlan;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import io.portone.sdk.server.common.PaymentAmountInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentScheduleService {
    private final PortOneService portOneService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    public String createSchedule(Subscription subscription, String billingKey, LocalDateTime periodStart, LocalDateTime periodEnd) {
        SubscriptionPlan nextBillingPlan = subscription.getNextBillingPlan();

        Invoice invoice = Invoice.create(
                subscription,
                nextBillingPlan.getPrice(),
                nextBillingPlan.getCurrency(),
                periodStart,
                periodEnd
        );
        invoiceRepository.save(invoice);

        Payment payment = Payment.create(
                invoice,
                nextBillingPlan.getPrice(),
                nextBillingPlan.getCurrency()
        );
        paymentRepository.save(payment);

        Instant timeToPay = periodStart
                .atZone(ZoneId.of("UTC"))
                .toInstant();
        String scheduleId = portOneService.createPaymentSchedule(
                payment.getId(),
                billingKey,
                nextBillingPlan.getProduct().getName() + " - 갱신",
                nextBillingPlan.getCurrency().toPortOneCurrency(),
                new PaymentAmountInput(nextBillingPlan.getPrice().longValue(), null, null),
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
                subscription.setCurrentPaymentScheduleId(null);
                log.info("결제 스케줄 교체: 기존 스케줄 취소 완료 - subscriptionId: {}, oldScheduleId: {}",
                        subscription.getId(), oldScheduleId);
            } catch (Exception e) {
                log.error("결제 스케줄 교체: 기존 스케줄 취소 실패 - subscriptionId: {}, oldScheduleId: {}",
                        subscription.getId(), oldScheduleId, e);
                throw new BusinessException(ApiStatusCode.INTERNAL_SERVER_ERROR,
                        "기존 결제 스케줄 취소 중 오류가 발생했습니다.");
            }
        }

        try {
            SubscriptionPlan nextBillingPlan = subscription.getNextBillingPlan();
            LocalDateTime nextPeriodStart = subscription.getCurrentPeriodEnd();
            LocalDateTime nextPeriodEnd = calculateNextPeriodEnd(
                    nextPeriodStart,
                    nextBillingPlan.getBillingCycle()
            );

            String newScheduleId = createSchedule(
                    subscription,
                    newPaymentMethod.getBillingKey(),
                    nextPeriodStart,
                    nextPeriodEnd
            );

            log.info("결제 스케줄 교체 완료 - subscriptionId: {}, newPaymentMethodId: {}, newScheduleId: {}",
                    subscription.getId(), newPaymentMethod.getId(), newScheduleId);
        } catch (Exception e) {
            log.error("결제 스케줄 교체: 새 스케줄 생성 실패 - subscriptionId: {}, newPaymentMethodId: {}",
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
