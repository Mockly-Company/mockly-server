package app.mockly.domain.product.service;

import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.entity.*;
import app.mockly.domain.payment.repository.InvoiceRepository;
import app.mockly.domain.payment.repository.OutboxEventRepository;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
import app.mockly.domain.payment.repository.PaymentRepository;
import app.mockly.domain.product.dto.request.CreateSubscriptionPlanChangeRequest;
import app.mockly.domain.product.dto.request.CreateSubscriptionRequest;
import app.mockly.domain.product.dto.response.CancelSubscriptionResponse;
import app.mockly.domain.product.dto.response.CreateSubscriptionPlanChangeResponse;
import app.mockly.domain.product.dto.response.CreateSubscriptionResponse;
import app.mockly.domain.product.dto.response.GetSubscriptionPlanChangeResponse;
import app.mockly.domain.product.dto.response.GetSubscriptionResponse;
import app.mockly.domain.product.entity.*;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import app.mockly.global.security.SensitiveDataMasker;
import io.portone.sdk.server.common.PaymentAmountInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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
    private final CurrentSubscriptionService currentSubscriptionService;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public CreateSubscriptionResponse createSubscription(UUID userId, CreateSubscriptionRequest request) {
        Integer planId = request.planId();
        BigDecimal expectedPrice = request.expectedPrice();
        Long paymentMethodId = request.paymentMethodId();

        // 플랜 조회 및 가격 검증을 먼저 수행해 존재하지 않는 플랜/무료 플랜 요청에 일관된 응답 제공
        SubscriptionPlan subscriptionPlan = subscriptionPlanRepository.findByIdWithProduct(planId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "플랜을 찾을 수 없습니다."));
        if (subscriptionPlan.isFree()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "무료 플랜은 회원가입 시 자동으로 부여됩니다.");
        }

        if (expectedPrice.compareTo(subscriptionPlan.getPrice()) != 0) {
            log.info("expectedPrice: {}, subscriptionPlan.getPrice: {}, isEqual: {}",
                    expectedPrice, subscriptionPlan.getPrice(), expectedPrice.equals(subscriptionPlan.getPrice()));
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "플랜 가격이 일치하지 않습니다.");
        }

        PaymentMethod paymentMethod = validateAndGetPaymentMethod(userId, paymentMethodId);
        String billingKey = paymentMethod.getBillingKey();

        lockUser(userId);
        currentSubscriptionService.findCurrentSubscription(userId).ifPresent(currentSubscription -> {
            if (!currentSubscription.isActive() || !currentSubscription.getSubscriptionPlan().isFree()) {
                throw new BusinessException(ApiStatusCode.BAD_REQUEST, "이미 현재 구독이 있습니다.");
            }
            currentSubscription.cancel();
            subscriptionRepository.flush();
        });

        Subscription subscription = Subscription.create(userId, subscriptionPlan, paymentMethod);
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

        try {
            PaymentAmountInput paymentAmountInput = new PaymentAmountInput(subscriptionPlan.getPrice().longValue(), null, null);
            String orderName = subscriptionPlan.getProduct().getName() + " - " + subscriptionPlan.getBillingCycle().name();
            portOneService.payWithBillingKey(
                    payment.getId(), billingKey, orderName, subscriptionPlan.getCurrency().toPortOneCurrency(), paymentAmountInput);

            payment.markAsPaid(PaymentMethodType.CARD);
            invoice.markAsPaid();
            subscription.activate();

            log.info("구독 생성 성공 - userId: {}, subscriptionId: {}, paymentId: {}, paymentMethodId: {}",
                    userId, subscription.getId(), payment.getId(), paymentMethodId);

            return CreateSubscriptionResponse.from(subscription);
        } catch (Exception e) {
            log.error("구독 생성 실패 - userId: {}, subscriptionId: {}, paymentId: {}", userId, subscription.getId(), payment.getId(), e);

            payment.markAsFailed(e.getMessage());
            invoice.markAsFailed();

            throw new BusinessException(ApiStatusCode.INTERNAL_SERVER_ERROR, "결제 처리 중 오류가 발생했습니다.");
        }
    }

    public GetSubscriptionResponse getMySubscription(UUID userId) {
        Subscription subscription = currentSubscriptionService.getCurrentSubscription(userId);
        PaymentMethod paymentMethod = resolvePaymentMethodForDisplay(userId, subscription).orElse(null);
        return GetSubscriptionResponse.from(subscription, paymentMethod);
    }

    @Transactional
    public CancelSubscriptionResponse cancelSubscription(UUID userId, Long subscriptionId) {
        return cancelSubscription(userId, subscriptionId, false);
    }

    @Transactional
    public CancelSubscriptionResponse cancelSubscription(UUID userId, Long subscriptionId, boolean immediate) {
        Subscription subscription = subscriptionRepository.findByIdWithDetails(subscriptionId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "구독을 찾을 수 없습니다"));

        if (!subscription.getUserId().equals(userId)) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "본인의 구독만 해지할 수 있습니다.");
        }
        if (!(subscription.isActive() || subscription.isPastDue() || subscription.isUnpaid())) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "이미 해지되었거나 비활성 상태입니다.");
        }
        if (subscription.getSubscriptionPlan().isFree()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "무료 플랜은 해지할 수 없습니다.");
        }

        revokeScheduleSafely(subscription);
        cancelPendingScheduleOutboxIfPresent(subscriptionId);

        if (subscription.isActive() && !immediate) {
            if (!subscription.isCancelAtPeriodEnd()) {
                subscription.scheduleCancelAtPeriodEnd();
            }
            subscriptionRepository.save(subscription);
            log.info("구독 기간 만료 해지 예약 완료 - userId: {}, subscriptionId: {}, availableUntil: {}",
                    userId, subscriptionId, subscription.getCurrentPeriodEnd());
            return CancelSubscriptionResponse.from(subscription);
        }

        lockUser(userId);
        subscription.cancel();
        subscriptionRepository.saveAndFlush(subscription);
        assignFreePlan(userId);

        log.info("구독 즉시 해지 및 Free 전환 완료 - userId: {}, subscriptionId: {}", userId, subscriptionId);
        return CancelSubscriptionResponse.from(subscription);
    }

    @Transactional
    public void completeExpiredCancelAtPeriodEndSubscription(Long subscriptionId, LocalDateTime now) {
        Subscription subscription = subscriptionRepository.findByIdWithDetails(subscriptionId)
                .orElse(null);
        if (subscription == null || !subscription.isActive() || !subscription.isCancelAtPeriodEnd()) {
            return;
        }
        if (subscription.getCurrentPeriodEnd() == null || subscription.getCurrentPeriodEnd().isAfter(now)) {
            return;
        }

        UUID userId = subscription.getUserId();
        lockUser(userId);
        revokeScheduleSafely(subscription);
        subscription.cancel();
        subscriptionRepository.saveAndFlush(subscription);
        assignFreePlan(userId);

        log.info("해지 예약 만료 구독 Free 전환 완료 - userId: {}, subscriptionId: {}", userId, subscriptionId);
    }

    @Transactional
    public void assignFreePlan(UUID userId) {
        lockUser(userId);
        if (currentSubscriptionService.findCurrentSubscription(userId).isPresent()) {
            return;
        }
        subscriptionPlanRepository.findActiveByPlanTierAndBillingCycle(PlanTier.FREE, BillingCycle.MONTHLY)
                .ifPresent(freePlan -> {
                    Subscription subscription = Subscription.create(userId, freePlan);
                    subscription.activate();
                    subscriptionRepository.save(subscription);
                });
    }

    private void lockUser(UUID userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));
    }

    @Transactional
    public void updatePaymentMethod(UUID userId, Long subscriptionId, Long newPaymentMethodId) {
        Subscription subscription = validateAndGetModifiableSubscription(userId, subscriptionId);
        PaymentMethod newPaymentMethod = validateAndGetPaymentMethod(userId, newPaymentMethodId);

        if (subscription.isPastDue() || subscription.isUnpaid()) {
            recoverDelinquentSubscription(subscription, newPaymentMethod);
            return;
        }

        if (subscription.isCancelAtPeriodEnd()) {
            subscription.changePaymentMethod(newPaymentMethod);
            return;
        }

        validatePaymentChangeTimestamp(subscription);
        paymentScheduleService.replaceSchedule(subscription, newPaymentMethod);
        subscription.changePaymentMethod(newPaymentMethod);
    }

    public GetSubscriptionPlanChangeResponse previewPlanChange(UUID userId, Integer targetPlanId) {
        if (targetPlanId == null) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "대상 플랜 ID(targetPlanId)는 필수입니다.");
        }

        Subscription subscription = getValidPaidActiveSubscriptionForPlanChange(userId);
        SubscriptionPlan currentPlan = subscription.getSubscriptionPlan();
        SubscriptionPlan targetPlan = getValidTargetPaidPlan(targetPlanId, currentPlan);

        LocalDateTime now = LocalDateTime.now();
        BigDecimal immediateAmount = calculateProratedImmediateAmount(subscription, currentPlan, targetPlan, now);
        BigDecimal nextBillingAmount = targetPlan.getPrice();
        LocalDateTime nextBillingDate = subscription.getCurrentPeriodEnd();

        return GetSubscriptionPlanChangeResponse.of(
                currentPlan,
                targetPlan,
                immediateAmount,
                nextBillingAmount,
                nextBillingDate
        );
    }

    @Transactional
    public CreateSubscriptionPlanChangeResponse changeSubscriptionPlan(
            UUID userId,
            CreateSubscriptionPlanChangeRequest request
    ) {
        lockUser(userId);

        Subscription subscription = getValidPaidActiveSubscriptionForPlanChange(userId);
        SubscriptionPlan currentPlan = subscription.getSubscriptionPlan();
        SubscriptionPlan targetPlan = getValidTargetPaidPlan(request.targetPlanId(), currentPlan);

        LocalDateTime now = LocalDateTime.now();
        BigDecimal expectedImmediateAmount = calculateProratedImmediateAmount(subscription, currentPlan, targetPlan, now);

        if (request.confirmedAmount().compareTo(expectedImmediateAmount) != 0) {
            throw new BusinessException(
                    ApiStatusCode.BAD_REQUEST,
                    "확인된 결제 금액이 예상 정산 금액과 일치하지 않습니다."
            );
        }

        boolean isUpgrade = targetPlan.getPrice().compareTo(currentPlan.getPrice()) > 0;
        if (isUpgrade) {
            PaymentMethod paymentMethod = resolveActivePaymentMethodForCharge(userId, subscription)
                    .orElseThrow(() -> new BusinessException(
                            ApiStatusCode.BAD_REQUEST,
                            "결제에 사용할 활성 결제 수단이 없습니다."
                    ));

            if (expectedImmediateAmount.compareTo(BigDecimal.ZERO) > 0) {
                chargeUpgradeDifference(subscription, targetPlan, paymentMethod, expectedImmediateAmount, now);
            }

            subscription.upgradePlanImmediately(targetPlan, paymentMethod);
            paymentScheduleService.replaceSchedule(subscription, paymentMethod);

            log.info("플랜 즉시 업그레이드 완료 - userId: {}, subscriptionId: {}, targetPlan: {}, chargedAmount: {}",
                    userId, subscription.getId(), targetPlan.getProduct().getName(), expectedImmediateAmount);

            return CreateSubscriptionPlanChangeResponse.of(
                    subscription,
                    expectedImmediateAmount,
                    targetPlan.getCurrency().name(),
                    now,
                    now
            );
        }

        // 다운그레이드: 현재 주기 말에 변경 예약
        subscription.scheduleDowngrade(targetPlan);
        resolveActivePaymentMethodForCharge(userId, subscription).ifPresent(paymentMethod -> {
            subscription.changePaymentMethod(paymentMethod);
            paymentScheduleService.replaceSchedule(subscription, paymentMethod);
        });

        log.info("플랜 다운그레이드 예약 완료 - userId: {}, subscriptionId: {}, scheduledPlan: {}, effectiveAt: {}",
                userId, subscription.getId(), targetPlan.getProduct().getName(), subscription.getCurrentPeriodEnd());

        return CreateSubscriptionPlanChangeResponse.of(
                subscription,
                BigDecimal.ZERO,
                targetPlan.getCurrency().name(),
                now,
                subscription.getCurrentPeriodEnd()
        );
    }

    @Transactional
    public void createFirstPaymentSchedule(Subscription subscription, String billingKey) {
        LocalDateTime nextPeriodStart = subscription.getCurrentPeriodEnd();
        LocalDateTime nextPeriodEnd = calculateNextPeriodEnd(
                nextPeriodStart,
                subscription.getNextBillingPlan().getBillingCycle()
        );
        String scheduleId = paymentScheduleService.createSchedule(subscription, billingKey, nextPeriodStart, nextPeriodEnd);

        log.info("첫 결제 스케줄 생성 완료 - subscriptionId: {}, scheduleId: {}, billingKey: {}, 다음 결제일: {}",
                subscription.getId(), scheduleId, SensitiveDataMasker.maskBillingKey(billingKey), nextPeriodStart);
    }

    @Transactional
    public void processScheduleCreation(Long subscriptionId) {
        OutboxEvent event = outboxEventRepository
                .findByAggregateIdAndEventTypeAndStatus(subscriptionId, "SCHEDULE_CREATE", OutboxEventStatus.PENDING)
                .orElse(null);
        if (event == null) {
            return;
        }

        Subscription subscription = subscriptionRepository.findByIdWithDetails(subscriptionId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "구독을 찾을 수 없습니다."));
        if (subscription.getCurrentPaymentScheduleId() != null
                || subscription.isCancelAtPeriodEnd()
                || subscription.isCanceled()) {
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
                subscription.getNextBillingPlan().getBillingCycle()
        );
        String scheduleId = paymentScheduleService.createSchedule(subscription, billingKey, nextPeriodStart, nextPeriodEnd);

        log.info("구독 갱신 완료 - subscriptionId: {}, scheduleId: {}, billingKey: {}, 현재 기간: {} ~ {}, 다음 결제일: {}",
                subscription.getId(),
                scheduleId,
                SensitiveDataMasker.maskBillingKey(billingKey),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                nextPeriodStart);
    }

    private void recoverDelinquentSubscription(Subscription subscription, PaymentMethod newPaymentMethod) {
        revokeScheduleSafely(subscription);

        SubscriptionPlan planToCharge = subscription.getNextBillingPlan();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodEnd = calculateNextPeriodEnd(now, planToCharge.getBillingCycle());

        Invoice invoice = invoiceRepository
                .findFirstBySubscriptionIdAndStatusInOrderByCreatedAtDesc(
                        subscription.getId(),
                        List.of(InvoiceStatus.FAILED, InvoiceStatus.PENDING)
                )
                .map(existingInvoice -> {
                    existingInvoice.updateForRecovery(planToCharge.getPrice(), now, periodEnd);
                    return existingInvoice;
                })
                .orElseGet(() -> invoiceRepository.save(Invoice.create(
                        subscription,
                        planToCharge.getPrice(),
                        planToCharge.getCurrency(),
                        now,
                        periodEnd
                )));

        Payment payment = Payment.create(invoice, planToCharge.getPrice(), planToCharge.getCurrency());
        paymentRepository.save(payment);

        try {
            PaymentAmountInput amountInput = new PaymentAmountInput(planToCharge.getPrice().longValue(), null, null);
            String orderName = planToCharge.getProduct().getName() + " - 연체 복구 결제";
            portOneService.payWithBillingKey(
                    payment.getId(),
                    newPaymentMethod.getBillingKey(),
                    orderName,
                    planToCharge.getCurrency().toPortOneCurrency(),
                    amountInput
            );
        } catch (Exception e) {
            log.error("연체 복구 결제 실패 - subscriptionId: {}, paymentId: {}, billingKey: {}",
                    subscription.getId(), payment.getId(),
                    SensitiveDataMasker.maskBillingKey(newPaymentMethod.getBillingKey()), e);
            throw new BusinessException(ApiStatusCode.INTERNAL_SERVER_ERROR, "미납 결제 승인 중 오류가 발생했습니다.");
        }

        payment.markAsPaid(PaymentMethodType.CARD);
        invoice.markAsPaid();
        subscription.recoverFromDelinquency(newPaymentMethod, now);

        LocalDateTime nextPeriodStart = subscription.getCurrentPeriodEnd();
        LocalDateTime nextPeriodEnd = calculateNextPeriodEnd(
                nextPeriodStart,
                subscription.getNextBillingPlan().getBillingCycle()
        );
        paymentScheduleService.createSchedule(
                subscription,
                newPaymentMethod.getBillingKey(),
                nextPeriodStart,
                nextPeriodEnd
        );

        log.info("연체 구독 복구 완료 - subscriptionId: {}, paymentId: {}, paymentMethodId: {}, 다음 결제일: {}",
                subscription.getId(), payment.getId(), newPaymentMethod.getId(), nextPeriodStart);
    }

    private void chargeUpgradeDifference(
            Subscription subscription,
            SubscriptionPlan targetPlan,
            PaymentMethod paymentMethod,
            BigDecimal immediateAmount,
            LocalDateTime now
    ) {
        Invoice invoice = Invoice.create(
                subscription,
                immediateAmount,
                targetPlan.getCurrency(),
                now,
                subscription.getCurrentPeriodEnd()
        );
        invoiceRepository.save(invoice);

        Payment payment = Payment.create(invoice, immediateAmount, targetPlan.getCurrency());
        paymentRepository.save(payment);

        try {
            PaymentAmountInput amountInput = new PaymentAmountInput(immediateAmount.longValue(), null, null);
            String orderName = targetPlan.getProduct().getName() + " - 플랜 업그레이드 차액";
            portOneService.payWithBillingKey(
                    payment.getId(),
                    paymentMethod.getBillingKey(),
                    orderName,
                    targetPlan.getCurrency().toPortOneCurrency(),
                    amountInput
            );
            payment.markAsPaid(PaymentMethodType.CARD);
            invoice.markAsPaid();
        } catch (Exception e) {
            log.error("플랜 업그레이드 차액 결제 실패 - subscriptionId: {}, paymentId: {}, billingKey: {}",
                    subscription.getId(), payment.getId(),
                    SensitiveDataMasker.maskBillingKey(paymentMethod.getBillingKey()), e);
            throw new BusinessException(ApiStatusCode.INTERNAL_SERVER_ERROR, "플랜 변경 차액 결제 중 오류가 발생했습니다.");
        }
    }

    private Subscription getValidPaidActiveSubscriptionForPlanChange(UUID userId) {
        Subscription subscription = currentSubscriptionService.getCurrentSubscription(userId);
        if (!subscription.isActive()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "활성 상태의 구독만 플랜을 변경할 수 있습니다.");
        }
        if (subscription.getSubscriptionPlan().isFree()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "무료 플랜에서는 신규 유료 구독 생성을 이용해주세요.");
        }
        return subscription;
    }

    private SubscriptionPlan getValidTargetPaidPlan(Integer targetPlanId, SubscriptionPlan currentPlan) {
        SubscriptionPlan targetPlan = subscriptionPlanRepository.findByIdWithProduct(targetPlanId)
                .filter(plan -> plan.getProduct().isActive())
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "대상 플랜을 찾을 수 없습니다."));

        if (targetPlan.isFree()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "무료 플랜으로는 변경할 수 없습니다. 구독 해지를 이용해주세요.");
        }
        if (targetPlan.getId().equals(currentPlan.getId())) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "현재 이용 중인 플랜과 동일한 플랜입니다.");
        }
        return targetPlan;
    }

    BigDecimal calculateProratedImmediateAmount(
            Subscription subscription,
            SubscriptionPlan currentPlan,
            SubscriptionPlan targetPlan,
            LocalDateTime now
    ) {
        BigDecimal priceDiff = targetPlan.getPrice().subtract(currentPlan.getPrice());
        if (priceDiff.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        LocalDateTime periodStart = subscription.getCurrentPeriodStart();
        LocalDateTime periodEnd = subscription.getCurrentPeriodEnd();
        if (periodStart == null || periodEnd == null || !now.isBefore(periodEnd)) {
            return BigDecimal.valueOf(priceDiff.longValue());
        }

        long totalDays = Math.max(1L, ChronoUnit.DAYS.between(periodStart.toLocalDate(), periodEnd.toLocalDate()));
        long remainingDays = Math.min(totalDays, ChronoUnit.DAYS.between(now.toLocalDate(), periodEnd.toLocalDate()));
        if (remainingDays <= 0L) {
            remainingDays = 1L;
        }

        BigDecimal prorated = priceDiff
                .multiply(BigDecimal.valueOf(remainingDays))
                .divide(BigDecimal.valueOf(totalDays), 0, RoundingMode.HALF_UP);

        if (prorated.compareTo(BigDecimal.ONE) < 0) {
            return BigDecimal.ONE;
        }
        return BigDecimal.valueOf(prorated.longValue());
    }

    private Optional<PaymentMethod> resolvePaymentMethodForDisplay(UUID userId, Subscription subscription) {
        if (subscription.getSubscriptionPlan().isFree()) {
            return Optional.empty();
        }
        return resolveActivePaymentMethodForCharge(userId, subscription);
    }

    private Optional<PaymentMethod> resolveActivePaymentMethodForCharge(UUID userId, Subscription subscription) {
        PaymentMethod linked = subscription.getPaymentMethod();
        if (linked != null && linked.isActive()) {
            return Optional.of(linked);
        }
        Optional<PaymentMethod> defaultMethod = paymentMethodRepository
                .findByUserIdAndIsDefaultTrueAndIsActiveTrue(userId);
        if (defaultMethod.isPresent()) {
            return defaultMethod;
        }
        return paymentMethodRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId)
                .stream()
                .findFirst();
    }

    private void revokeScheduleSafely(Subscription subscription) {
        String scheduleId = subscription.getCurrentPaymentScheduleId();
        if (scheduleId == null) {
            return;
        }
        try {
            portOneService.revokePaymentSchedule(scheduleId);
            log.info("결제 스케줄 취소 완료 - subscriptionId: {}, scheduleId: {}",
                    subscription.getId(), scheduleId);
        } catch (Exception e) {
            log.error("결제 스케줄 취소 실패 - subscriptionId: {}, scheduleId: {}",
                    subscription.getId(), scheduleId, e);
        } finally {
            subscription.setCurrentPaymentScheduleId(null);
        }
    }

    private void cancelPendingScheduleOutboxIfPresent(Long subscriptionId) {
        outboxEventRepository
                .findByAggregateIdAndEventTypeAndStatus(subscriptionId, "SCHEDULE_CREATE", OutboxEventStatus.PENDING)
                .ifPresent(OutboxEvent::markAsProcessed);
    }

    private Subscription validateAndGetModifiableSubscription(UUID userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findByIdWithDetails(subscriptionId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "구독을 찾을 수 없습니다."));

        if (!subscription.getUserId().equals(userId)) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "본인의 구독만 수정할 수 있습니다.");
        }
        if (!(subscription.isActive() || subscription.isPastDue() || subscription.isUnpaid())) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "결제 수단을 변경할 수 없는 구독 상태입니다.");
        }
        if (subscription.getSubscriptionPlan().isFree()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "무료 플랜은 결제 수단을 변경할 수 없습니다.");
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
        if (nextPaymentTime == null) {
            return;
        }
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
