package app.mockly.domain.payment.service;

import app.mockly.domain.payment.entity.Payment;
import app.mockly.domain.payment.entity.PaymentMethodType;
import app.mockly.domain.payment.entity.PaymentStatus;
import app.mockly.domain.payment.repository.PaymentRepository;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionStatus;
import app.mockly.domain.product.service.SubscriptionService;
import app.mockly.global.config.PortOneProperties;
import io.portone.sdk.server.PortOneClient;
import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.payment.FailedPayment;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.PaymentFailure;
import io.portone.sdk.server.webhook.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {
    private final PortOneProperties portOneProperties;
    private final PortOneClient portOneClient;

    private final SubscriptionService subscriptionService;

    private final PaymentRepository paymentRepository;

    @Transactional
    public void processWebhook(String rawBody, String webhookId, String signature, String timestamp) throws WebhookVerificationException {
        WebhookVerifier verifier = new WebhookVerifier(portOneProperties.webhookSecret());
        Webhook webhook = verifier.verify(rawBody, webhookId, signature, timestamp);

        switch (webhook) {
            case WebhookTransactionPaid paid -> handleTransactionPaid(paid.getData());
            case WebhookTransactionFailed failed -> handleTransactionFailed(failed.getData());
            case WebhookTransactionCancelled canceled -> handleTransactionCanceled(canceled.getData());
            default -> log.info("처리하지 않는 웹훅 타입: {}", webhook.getClass().getSimpleName());
        }
    }

    private void handleTransactionPaid(WebhookTransactionDataPaid data) {
        String paymentId = data.getPaymentId();
        Payment payment = paymentRepository.findById(paymentId)
                .orElse(null);
        if (payment == null) {
            log.warn("알 수 없는 결제: {}", paymentId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("이미 처리된 결제: {}", paymentId);
            return;
        }

        io.portone.sdk.server.payment.Payment paymentInfo = portOneClient.getPayment().getPayment(paymentId).join();
        if (!(paymentInfo instanceof PaidPayment paidPayment)) {
            log.warn("결제가 완료 상태가 아닙니다: {}", paymentId);
            return;
        }
        PaymentMethodType paymentMethodType = PaymentMethodType.from(paidPayment.getMethod());

        String billingKey = paidPayment.getBillingKey(); // 결제에 사용된 빌링키
        if (billingKey == null) {
            log.error("결제에서 빌링키를 찾을 수 없습니다 - paymentId: {}", paymentId);
            return;
        }

        payment.markAsPaid(paymentMethodType);
        payment.getInvoice().markAsPaid();

        Subscription subscription = payment.getInvoice().getSubscription();

        // 중복 방지: 이미 스케줄이 생성되어 있으면 동기 처리 완료된 것으로 간주
        if (subscription.getCurrentPaymentScheduleId() != null) {
            log.info("이미 처리됨 (스케줄 존재), Webhook 스킵 - subscriptionId: {}, scheduleId: {}",
                    subscription.getId(), subscription.getCurrentPaymentScheduleId());
            return;
        }

        if (subscription.getStatus() == SubscriptionStatus.PENDING) { // 첫 결제: activate + 첫 스케줄 생성
            subscription.activate();
            try {
                subscriptionService.createFirstPaymentSchedule(subscription, billingKey);
                log.info("첫 결제 완료 및 구독 활성화 (Webhook 보정): {}, billingKey: {}", subscription.getId(), billingKey);
            } catch (Exception e) {
                subscription.markAsPastDue();
                log.error("첫 결제 스케줄 생성 실패, 구독 PAST_DUE 전환 - subscriptionId: {}, billingKey: {}",
                        subscription.getId(), billingKey, e);
            }
        } else if (subscription.isActive() && !subscription.isCanceled()) { // 갱신 결제: 구독 갱신 + 다음 스케줄 생성
            try {
                subscriptionService.renewSubscription(subscription, billingKey);
                log.info("구독 갱신 완료 (Webhook): {}, billingKey: {}", subscription.getId(), billingKey);
            } catch (Exception e) {
                subscription.markAsPastDue();
                log.error("구독 갱신 실패, 구독 PAST_DUE 전환 - subscriptionId: {}, billingKey: {}",
                        subscription.getId(), billingKey, e);
            }
        }
    }

    private void handleTransactionFailed(WebhookTransactionDataFailed data) {
        String paymentId = data.getPaymentId();
        Payment payment = paymentRepository.findById(paymentId).orElse(null);

        if (payment == null) {
            log.warn("알 수 없는 결제: {}", paymentId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            log.info("이미 실패 처리된 결제: {}", paymentId);
            return;
        }

        // PortOne API로 실패 사유 조회
        String failReason = "실패 메세지";
        io.portone.sdk.server.payment.Payment paymentInfo = portOneClient.getPayment().getPayment(paymentId).join();
        if (paymentInfo instanceof FailedPayment failedPayment) {
            PaymentFailure failure = failedPayment.getFailure();
            if (failure.getReason() != null) {
                failReason = failure.getReason();
            } else if (failure.getPgMessage() != null) {
                failReason = failure.getPgMessage();
            }
        }

        payment.markAsFailed(failReason);
        payment.getInvoice().markAsFailed();

        // 갱신 결제 실패 시 구독을 PAST_DUE 상태로 변경
        Subscription subscription = payment.getInvoice().getSubscription();
        if (subscription.isActive()) {
            subscription.markAsPastDue();
            log.warn("갱신 결제 실패로 구독 PAST_DUE 전환 - subscriptionId: {}, paymentId: {}, 실패 이유: {}",
                    subscription.getId(), paymentId, failReason);
        } else {
            log.info("결제 실패 - paymentId: {}, subscriptionStatus: {}, 실패 이유: {}",
                    paymentId, subscription.getStatus(), failReason);
        }
    }

    private void handleTransactionCanceled(WebhookTransactionCancelledData data) {
        String paymentId = data.getPaymentId();
        Payment payment = paymentRepository.findById(paymentId).orElse(null);

        if (payment == null) {
            log.warn("알 수 없는 결제: {}", paymentId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.CANCELED) {
            log.info("이미 취소 처리된 결제: {}", paymentId);
            return;
        }

        payment.cancel();
        payment.getInvoice().getSubscription().cancel();
        log.info("결제 취소 - paymentId: {}", paymentId);
    }
}
