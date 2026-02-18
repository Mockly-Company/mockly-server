package app.mockly.domain.product.service;

import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.entity.*;
import app.mockly.domain.payment.repository.InvoiceRepository;
import app.mockly.domain.payment.repository.OutboxEventRepository;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
import app.mockly.domain.payment.repository.PaymentRepository;
import app.mockly.domain.product.dto.request.CreateSubscriptionRequest;
import app.mockly.domain.product.dto.response.CancelSubscriptionResponse;
import app.mockly.domain.product.dto.response.CreateSubscriptionResponse;
import app.mockly.domain.product.dto.response.GetSubscriptionResponse;
import app.mockly.domain.product.entity.*;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import io.portone.sdk.server.common.PaymentAmountInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {
    private final PaymentMethodRepository paymentMethodRepository;
    private final PortOneService portOneService;
    private final PaymentScheduleService paymentScheduleService;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public CreateSubscriptionResponse createSubscription(UUID userId, CreateSubscriptionRequest request) {
        Integer planId = request.planId();
        BigDecimal expectedPrice = request.expectedPrice();
        Long paymentMethodId = request.paymentMethodId();

        // PaymentMethod 조회 및 권한 검증
        PaymentMethod paymentMethod = paymentMethodRepository
                .findById(paymentMethodId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "결제 수단을 찾을 수 없습니다."));

        if (!paymentMethod.getUser().getId().equals(userId)) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "본인의 결제 수단만 사용할 수 있습니다.");
        }

        String billingKey = paymentMethod.getBillingKey();

        // 플랜 조회 및 가격 검증
        SubscriptionPlan subscriptionPlan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "플랜을 찾을 수 없습니다."));
        if (subscriptionPlan.isFree()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "무료 플랜은 SMS 인증 후 자동으로 부여됩니다.");
        }

        if (expectedPrice.compareTo(subscriptionPlan.getPrice()) != 0) {
            log.info("expectedPrice: {}, subscriptionPlan.getPrice: {}, isEqual: {}", expectedPrice.toString(), subscriptionPlan.getPrice().toString(), expectedPrice.equals(subscriptionPlan.getPrice()));
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "플랜 가격이 일치하지 않습니다.");
        }

        // 동일 플랜 중복 구독 확인
        subscriptionRepository.findByUserIdAndPlanIdAndStatus(userId, planId, SubscriptionStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new BusinessException(ApiStatusCode.BAD_REQUEST, "이미 해당 플랜을 구독중입니다.");
                });

        // 기존 구독 확인 및 취소
        subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .ifPresent(currentPlan -> {
                    if (currentPlan.getSubscriptionPlan().isFree()) {
                        currentPlan.cancel();
                    } else {
                        throw new BusinessException(ApiStatusCode.BAD_REQUEST, "이미 구독중인 플랜이 있습니다. 구독 변경 API를 사용하세요.");
                    }
                });

        // Subscription(PENDING) + Invoice(PENDING) + Payment(PENDING) 생성
        Subscription subscription = Subscription.create(userId, subscriptionPlan);
        subscriptionRepository.save(subscription);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodEnd = subscriptionPlan.getBillingCycle().equals(BillingCycle.MONTHLY)
                ? now.plusMonths(1)
                : now.plusYears(1);
        Invoice invoice = Invoice.create(subscription, subscriptionPlan.getPrice(), subscriptionPlan.getCurrency(), now, periodEnd);
        invoiceRepository.save(invoice);

        Payment payment = Payment.create(invoice, subscriptionPlan.getPrice(), subscriptionPlan.getCurrency());
        paymentRepository.save(payment);

        outboxEventRepository.save(OutboxEvent.scheduleCreate(subscription.getId(), billingKey));

        // 결제 요청
        try {
            PaymentAmountInput paymentAmountInput = new PaymentAmountInput(subscriptionPlan.getPrice().longValue(), null, null);
            String orderName = subscriptionPlan.getProduct().getName() + " - " + subscriptionPlan.getBillingCycle().name();
            portOneService.payWithBillingKey(
                    payment.getId(), billingKey, orderName, subscriptionPlan.getCurrency().toPortOneCurrency(), paymentAmountInput);

            // 결제 성공 처리 (현재 카드만 지원)
            payment.markAsPaid(PaymentMethodType.CARD);
            invoice.markAsPaid();
            subscription.activate();

            log.info("구독 생성 성공 - userId: {}, subscriptionId: {}, paymentId: {}, paymentMethodId: {}",
                    userId, subscription.getId(), payment.getId(), paymentMethodId);

            return CreateSubscriptionResponse.from(subscription);
        } catch (Exception e) {
            log.error("구독 생성 실패 - userId: {}, subscriptionId: {}, paymentId: {}", userId, subscription.getId(), payment.getId());

            payment.markAsFailed(e.getMessage());
            invoice.markAsFailed();

            // Outbox도 함께 롤백됨 (같은 @Transactional)
            throw new BusinessException(ApiStatusCode.INTERNAL_SERVER_ERROR, "결제 처리 중 오류가 발생했습니다.");
        }
    }

    public GetSubscriptionResponse getMySubscription(UUID userId) {
        return subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .map(GetSubscriptionResponse::from)
                .orElse(null);
    }

    @Transactional
    public CancelSubscriptionResponse cancelSubscription(UUID userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "구독을 찾을 수 없습니다"));

        if (!subscription.getUserId().equals(userId)) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "본인의 구독만 해지할 수 있습니다.");
        }
        if (!subscription.isActive()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "이미 해지되었거나 비활성 상태입니다.");
        }

        // 결제 스케줄 취소
        if (subscription.getCurrentPaymentScheduleId() != null) {
            try {
                portOneService.revokePaymentSchedule(subscription.getCurrentPaymentScheduleId());
                log.info("결제 스케줄 취소 완료 - subscriptionId: {}, scheduleId: {}",
                        subscriptionId, subscription.getCurrentPaymentScheduleId());
            } catch (Exception e) {
                log.error("결제 스케줄 취소 실패 - subscriptionId: {}, scheduleId: {}",
                        subscriptionId, subscription.getCurrentPaymentScheduleId(), e);
                // 실패해도 구독은 취소 처리 (스케줄은 수동 정리 필요)
            }
        }

        subscription.cancel();
        subscriptionRepository.save(subscription);

        return CancelSubscriptionResponse.from(subscription);
    }

    public void assignFreePlan(UUID userId) {
        subscriptionPlanRepository.findByBillingCycle(BillingCycle.LIFETIME)
                .ifPresent(freePLan -> {
                    Subscription subscription = Subscription.create(userId, freePLan);
                    subscription.activate();
                    subscriptionRepository.save(subscription);
                });
    }

    @Transactional
    public void updatePaymentMethod(UUID userId, Long subscriptionId, Long newPaymentMethodId) {
        Subscription subscription = validateAndGetSubscription(userId, subscriptionId);
        PaymentMethod newPaymentMethod = validateAndGetPaymentMethod(userId, newPaymentMethodId);
        validatePaymentChangeTimestamp(subscription);

        paymentScheduleService.replaceSchedule(subscription, newPaymentMethod);
    }


    @Transactional
    public void createFirstPaymentSchedule(Subscription subscription, String billingKey) {
        LocalDateTime nextPeriodStart = subscription.getCurrentPeriodEnd();
        LocalDateTime nextPeriodEnd = calculateNextPeriodEnd(
                nextPeriodStart,
                subscription.getSubscriptionPlan().getBillingCycle()
        );
        String scheduleId = paymentScheduleService.createSchedule(subscription, billingKey, nextPeriodStart, nextPeriodEnd);

        log.info("첫 결제 스케줄 생성 완료 - subscriptionId: {}, scheduleId: {}, billingKey: {}, 다음 결제일: {}",
                subscription.getId(), scheduleId, billingKey, nextPeriodStart);
    }

    @Transactional
    public void processScheduleCreation(Long subscriptionId) {
        OutboxEvent event = outboxEventRepository.findByAggregateIdAndEventTypeAndStatus(subscriptionId, "SCHEDULE_CREATE", OutboxEventStatus.PENDING)
                .orElse(null);
        if (event == null) return;

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "구독을 찾을 수 없습니다."));
        // 이미 스케줄이 생성되어 있는 경우, 중복 방지 (ex. 기본 결제 수단 변경)
        if (subscription.getCurrentPaymentScheduleId() != null) {
            event.markAsProcessed();
            return;
        }

        String billingKey = event.extractBillingKey();

        try {
            createFirstPaymentSchedule(subscription, billingKey);
            event.markAsProcessed();
        } catch (BusinessException e) {
            if (e.getStatusCode() == ApiStatusCode.DUPLICATE_RESOURCE) {
                log.warn("PortOne에 스케줄 존재하나 DB에 scheduleId 없음 - subscriptionId: {}", subscriptionId);
                event.markAsFailed("PAYMENT_SCHEDULE_ALREADY_EXISTS - 수동 확인 필요");
            }
            throw e;
        }
    }

    @Transactional
    public void renewSubscription(Subscription subscription, String billingKey) {
        subscription.extendPeriod();

        LocalDateTime nextPeriodStart = subscription.getCurrentPeriodEnd();
        LocalDateTime nextPeriodEnd = calculateNextPeriodEnd(
                nextPeriodStart,
                subscription.getSubscriptionPlan().getBillingCycle()
        );
        String scheduleId = paymentScheduleService.createSchedule(subscription, billingKey, nextPeriodStart, nextPeriodEnd);

        log.info("구독 갱신 완료 - subscriptionId: {}, scheduleId: {}, billingKey: {}, 현재 기간: {} ~ {}, 다음 결제일: {}",
                subscription.getId(), scheduleId, billingKey, subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd(), nextPeriodStart);
    }

    private Subscription validateAndGetSubscription(UUID userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "구독을 찾을 수 없습니다."));

        if (!subscription.getUserId().equals(userId)) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "본인의 구독만 수정할 수 있습니다.");
        }
        if (!subscription.isActive()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "활성 상태인 구독만 결제 수단을 변경할 수 있습니다.");
        }
        return subscription;
    }

    private PaymentMethod validateAndGetPaymentMethod(UUID userId, Long paymentMethodId) {
        PaymentMethod paymentMethod = paymentMethodRepository.findById(paymentMethodId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "결제 수단을 찾을 수 없습니다."));

        if (!paymentMethod.getUser().getId().equals(userId)) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "본인의 결제 수단만 사용할 수 있습니다.");
        }
        if (!paymentMethod.isActive()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "비활성화된 결제 수단은 사용할 수 없습니다.");
        }
        return paymentMethod;
    }

    private void validatePaymentChangeTimestamp(Subscription subscription) {
        LocalDateTime nextPaymentTime = subscription.getCurrentPeriodEnd();
        LocalDateTime oneHourBeforePayment = nextPaymentTime.minusHours(1);

        if (LocalDateTime.now().isAfter(oneHourBeforePayment)) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST,
                    "다음 결제 1시간 전에는 결제 수단을 변경할 수 없습니다. 다음 결제일: " + nextPaymentTime);
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
