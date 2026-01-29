package app.mockly.domain.payment.service;

import app.mockly.domain.payment.entity.Payment;
import app.mockly.domain.payment.entity.PaymentMethodType;
import app.mockly.domain.payment.entity.PaymentStatus;
import app.mockly.domain.payment.repository.PaymentRepository;
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

        if (payment.getStatus().equals(PaymentStatus.PAID)) {
            log.info("이미 처리된 결제: {}", paymentId);
            return;
        }

        io.portone.sdk.server.payment.Payment paymentInfo = portOneClient.getPayment().getPayment(paymentId).join();
        if (!(paymentInfo instanceof PaidPayment paidPayment)) {
            log.warn("결제가 완료 상태가 아닙니다: {}", paymentId);
            return;
        }
        PaymentMethodType paymentMethodType = PaymentMethodType.from(paidPayment.getMethod());

        payment.markAsPaid(paymentMethodType);
        payment.getInvoice().markAsPaid();
        payment.getInvoice().getSubscription().activate();

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
        log.info("결제 실패 - paymentId: {}, 실패 이유: {}", paymentId, failReason);
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
