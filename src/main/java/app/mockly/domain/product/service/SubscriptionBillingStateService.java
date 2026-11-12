package app.mockly.domain.product.service;

import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.payment.entity.Invoice;
import app.mockly.domain.payment.entity.InvoiceStatus;
import app.mockly.domain.payment.entity.OutboxEvent;
import app.mockly.domain.payment.entity.OutboxEventStatus;
import app.mockly.domain.payment.entity.Payment;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.entity.PaymentMethodType;
import app.mockly.domain.payment.repository.InvoiceRepository;
import app.mockly.domain.payment.repository.OutboxEventRepository;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
import app.mockly.domain.payment.repository.PaymentRepository;
import app.mockly.domain.product.dto.request.CreateSubscriptionPlanChangeRequest;
import app.mockly.domain.product.dto.response.CancelSubscriptionResponse;
import app.mockly.domain.product.dto.response.CreateSubscriptionPlanChangeResponse;
import app.mockly.domain.product.entity.BillingCycle;
import app.mockly.domain.product.entity.Currency;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.entity.SubscriptionPlan;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionBillingStateService {

    private final SubscriptionRepository subscriptionRepository;
    private final CurrentSubscriptionService currentSubscriptionService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final UserRepository userRepository;

    public record CancelStateResult(
            CancelSubscriptionResponse response,
            String oldScheduleId
    ) {
    }

    public record ExpiredPeriodEndResult(
            UUID userId,
            String oldScheduleId
    ) {
    }

    public record ScheduleSyncTarget(
            Long subscriptionId,
            String oldScheduleId,
            String billingKey
    ) {
    }

    public record NextScheduleAttempt(
            String invoiceId,
            String paymentId,
            String orderName,
            Currency currency,
            BigDecimal amount,
            Instant timeToPay,
            LocalDateTime nextPeriodStart
    ) {
    }

    public sealed interface PaymentMethodUpdatePlan
            permits PaymentMethodUpdatePlan.Completed,
                    PaymentMethodUpdatePlan.ScheduleSyncOnly,
                    PaymentMethodUpdatePlan.DelinquentRecovery {

        record Completed() implements PaymentMethodUpdatePlan {
        }

        record ScheduleSyncOnly(
                Long subscriptionId,
                Long newPaymentMethodId,
                String oldScheduleId,
                String billingKey
        ) implements PaymentMethodUpdatePlan {
        }

        record DelinquentRecovery(
                Long subscriptionId,
                Long newPaymentMethodId,
                String invoiceId,
                String paymentId,
                String billingKey,
                String orderName,
                Currency currency,
                BigDecimal amount,
                LocalDateTime recoveryNow
        ) implements PaymentMethodUpdatePlan {
        }
    }

    public sealed interface PlanChangePlan
            permits PlanChangePlan.DowngradeCompleted,
                    PlanChangePlan.UpgradeChargeRequired {

        record DowngradeCompleted(
                Long subscriptionId,
                CreateSubscriptionPlanChangeResponse response,
                String oldScheduleId,
                String billingKey
        ) implements PlanChangePlan {
        }

        record UpgradeChargeRequired(
                Long subscriptionId,
                Integer targetPlanId,
                Long paymentMethodId,
                String invoiceId,
                String paymentId,
                String billingKey,
                String orderName,
                Currency currency,
                BigDecimal immediateAmount,
                LocalDateTime requestedAt
        ) implements PlanChangePlan {
        }
    }

    public record PlanUpgradeCompletionResult(
            CreateSubscriptionPlanChangeResponse response,
            String oldScheduleId,
            String billingKey
    ) {
    }

    @Transactional
    public CancelStateResult cancelSubscriptionState(UUID userId, Long subscriptionId, boolean immediate) {
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

        String oldScheduleId = subscription.getCurrentPaymentScheduleId();
        cancelPendingScheduleOutboxIfPresent(subscriptionId);

        if (subscription.isActive() && !immediate) {
            if (!subscription.isCancelAtPeriodEnd()) {
                subscription.scheduleCancelAtPeriodEnd();
            } else {
                subscription.setCurrentPaymentScheduleId(null);
            }
            subscriptionRepository.save(subscription);
            log.info("구독 기간 만료 해지 예약 완료 - userId: {}, subscriptionId: {}, availableUntil: {}",
                    userId, subscriptionId, subscription.getCurrentPeriodEnd());
            return new CancelStateResult(CancelSubscriptionResponse.from(subscription), oldScheduleId);
        }

        lockUser(userId);
        subscription.cancel();
        subscriptionRepository.saveAndFlush(subscription);
        assignFreePlanIfNoCurrent(userId);

        log.info("구독 즉시 해지 및 Free 전환 완료 - userId: {}, subscriptionId: {}", userId, subscriptionId);
        return new CancelStateResult(CancelSubscriptionResponse.from(subscription), oldScheduleId);
    }

    @Transactional
    public Optional<ExpiredPeriodEndResult> transitionExpiredCancelAtPeriodEnd(Long subscriptionId, LocalDateTime now) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null || !subscription.isActive() || !subscription.isCancelAtPeriodEnd()) {
            return Optional.empty();
        }
        if (subscription.getCurrentPeriodEnd() == null || subscription.getCurrentPeriodEnd().isAfter(now)) {
            return Optional.empty();
        }

        UUID userId = subscription.getUserId();
        String oldScheduleId = subscription.getCurrentPaymentScheduleId();

        lockUser(userId);
        int updated = subscriptionRepository.cancelExpiredPeriodEndAtomically(subscriptionId, now, LocalDateTime.now());
        if (updated != 1) {
            return Optional.empty();
        }

        cancelPendingScheduleOutboxIfPresent(subscriptionId);
        assignFreePlanIfNoCurrent(userId);

        log.info("해지 예약 만료 구독 Free 전환 완료 - userId: {}, subscriptionId: {}", userId, subscriptionId);
        return Optional.of(new ExpiredPeriodEndResult(userId, oldScheduleId));
    }

    @Transactional
    public void assignFreePlan(UUID userId) {
        lockUser(userId);
        assignFreePlanIfNoCurrent(userId);
    }

    @Transactional
    public PaymentMethodUpdatePlan preparePaymentMethodUpdate(
            UUID userId,
            Long subscriptionId,
            Long newPaymentMethodId
    ) {
        lockUser(userId);
        Subscription subscription = validateAndGetModifiableSubscription(userId, subscriptionId);
        PaymentMethod newPaymentMethod = validateAndGetPaymentMethod(userId, newPaymentMethodId);

        if (subscription.isPastDue() || subscription.isUnpaid()) {
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

            Payment payment = paymentRepository.save(
                    Payment.create(invoice, planToCharge.getPrice(), planToCharge.getCurrency())
            );

            return new PaymentMethodUpdatePlan.DelinquentRecovery(
                    subscription.getId(),
                    newPaymentMethod.getId(),
                    invoice.getId(),
                    payment.getId(),
                    newPaymentMethod.getBillingKey(),
                    planToCharge.getProduct().getName() + " - 연체 복구 결제",
                    planToCharge.getCurrency(),
                    planToCharge.getPrice(),
                    now
            );
        }

        if (subscription.isCancelAtPeriodEnd()) {
            subscription.changePaymentMethod(newPaymentMethod);
            return new PaymentMethodUpdatePlan.Completed();
        }

        validatePaymentChangeTimestamp(subscription);
        String oldScheduleId = subscription.getCurrentPaymentScheduleId();
        subscription.changePaymentMethod(newPaymentMethod);
        subscription.setCurrentPaymentScheduleId(null);
        enqueueScheduleCreateOutbox(subscription.getId(), newPaymentMethod.getBillingKey());

        return new PaymentMethodUpdatePlan.ScheduleSyncOnly(
                subscription.getId(),
                newPaymentMethod.getId(),
                oldScheduleId,
                newPaymentMethod.getBillingKey()
        );
    }

    @Transactional
    public ScheduleSyncTarget completeDelinquentRecovery(
            UUID userId,
            PaymentMethodUpdatePlan.DelinquentRecovery recoveryPlan
    ) {
        lockUser(userId);
        Subscription subscription = subscriptionRepository.findByIdWithDetails(recoveryPlan.subscriptionId())
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "구독을 찾을 수 없습니다."));
        PaymentMethod newPaymentMethod = validateAndGetPaymentMethod(userId, recoveryPlan.newPaymentMethodId());

        paymentRepository.findById(recoveryPlan.paymentId())
                .ifPresent(payment -> payment.markAsPaid(PaymentMethodType.CARD));
        invoiceRepository.findById(recoveryPlan.invoiceId())
                .ifPresent(Invoice::markAsPaid);

        String oldScheduleId = subscription.getCurrentPaymentScheduleId();
        subscription.recoverFromDelinquency(newPaymentMethod, recoveryPlan.recoveryNow());
        subscription.setCurrentPaymentScheduleId(null);
        enqueueScheduleCreateOutbox(subscription.getId(), newPaymentMethod.getBillingKey());

        return new ScheduleSyncTarget(
                subscription.getId(),
                oldScheduleId,
                newPaymentMethod.getBillingKey()
        );
    }

    @Transactional
    public PlanChangePlan preparePlanChange(UUID userId, CreateSubscriptionPlanChangeRequest request) {
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

            String invoiceId = null;
            String paymentId = null;
            if (expectedImmediateAmount.compareTo(BigDecimal.ZERO) > 0) {
                Invoice invoice = invoiceRepository.save(Invoice.create(
                        subscription,
                        expectedImmediateAmount,
                        targetPlan.getCurrency(),
                        now,
                        subscription.getCurrentPeriodEnd()
                ));
                Payment payment = paymentRepository.save(
                        Payment.create(invoice, expectedImmediateAmount, targetPlan.getCurrency())
                );
                invoiceId = invoice.getId();
                paymentId = payment.getId();
            }

            return new PlanChangePlan.UpgradeChargeRequired(
                    subscription.getId(),
                    targetPlan.getId(),
                    paymentMethod.getId(),
                    invoiceId,
                    paymentId,
                    paymentMethod.getBillingKey(),
                    targetPlan.getProduct().getName() + " - 플랜 업그레이드 차액",
                    targetPlan.getCurrency(),
                    expectedImmediateAmount,
                    now
            );
        }

        subscription.scheduleDowngrade(targetPlan);
        String oldScheduleId = null;
        String billingKey = null;
        Optional<PaymentMethod> activePaymentMethod = resolveActivePaymentMethodForCharge(userId, subscription);
        if (activePaymentMethod.isPresent()) {
            PaymentMethod paymentMethod = activePaymentMethod.get();
            oldScheduleId = subscription.getCurrentPaymentScheduleId();
            subscription.changePaymentMethod(paymentMethod);
            subscription.setCurrentPaymentScheduleId(null);
            billingKey = paymentMethod.getBillingKey();
            enqueueScheduleCreateOutbox(subscription.getId(), billingKey);
        }

        CreateSubscriptionPlanChangeResponse response = CreateSubscriptionPlanChangeResponse.of(
                subscription,
                BigDecimal.ZERO,
                targetPlan.getCurrency().name(),
                now,
                subscription.getCurrentPeriodEnd()
        );

        log.info("플랜 다운그레이드 예약 완료 - userId: {}, subscriptionId: {}, scheduledPlan: {}, effectiveAt: {}",
                userId, subscription.getId(), targetPlan.getProduct().getName(), subscription.getCurrentPeriodEnd());

        return new PlanChangePlan.DowngradeCompleted(
                subscription.getId(),
                response,
                oldScheduleId,
                billingKey
        );
    }

    @Transactional
    public PlanUpgradeCompletionResult completePlanUpgrade(
            UUID userId,
            PlanChangePlan.UpgradeChargeRequired upgradePlan
    ) {
        lockUser(userId);
        Subscription subscription = subscriptionRepository.findByIdWithDetails(upgradePlan.subscriptionId())
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "구독을 찾을 수 없습니다."));
        SubscriptionPlan targetPlan = subscriptionPlanRepository.findByIdWithProduct(upgradePlan.targetPlanId())
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "대상 플랜을 찾을 수 없습니다."));
        PaymentMethod paymentMethod = validateAndGetPaymentMethod(userId, upgradePlan.paymentMethodId());

        if (upgradePlan.paymentId() != null) {
            paymentRepository.findById(upgradePlan.paymentId())
                    .ifPresent(payment -> payment.markAsPaid(PaymentMethodType.CARD));
        }
        if (upgradePlan.invoiceId() != null) {
            invoiceRepository.findById(upgradePlan.invoiceId())
                    .ifPresent(Invoice::markAsPaid);
        }

        String oldScheduleId = subscription.getCurrentPaymentScheduleId();
        subscription.upgradePlanImmediately(targetPlan, paymentMethod);
        subscription.setCurrentPaymentScheduleId(null);
        enqueueScheduleCreateOutbox(subscription.getId(), paymentMethod.getBillingKey());

        CreateSubscriptionPlanChangeResponse response = CreateSubscriptionPlanChangeResponse.of(
                subscription,
                upgradePlan.immediateAmount(),
                targetPlan.getCurrency().name(),
                upgradePlan.requestedAt(),
                upgradePlan.requestedAt()
        );

        log.info("플랜 즉시 업그레이드 완료 - userId: {}, subscriptionId: {}, targetPlan: {}, chargedAmount: {}",
                userId, subscription.getId(), targetPlan.getProduct().getName(), upgradePlan.immediateAmount());

        return new PlanUpgradeCompletionResult(
                response,
                oldScheduleId,
                paymentMethod.getBillingKey()
        );
    }

    @Transactional
    public void markChargeFailed(String invoiceId, String paymentId, String failReason) {
        if (paymentId != null) {
            paymentRepository.findById(paymentId)
                    .ifPresent(payment -> payment.markAsFailed(failReason));
        }
        if (invoiceId != null) {
            invoiceRepository.findById(invoiceId)
                    .ifPresent(Invoice::markAsFailed);
        }
    }

    @Transactional
    public Optional<NextScheduleAttempt> prepareNextScheduleAttempt(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findByIdWithDetails(subscriptionId)
                .orElse(null);
        if (subscription == null
                || !subscription.isActive()
                || subscription.isCancelAtPeriodEnd()
                || subscription.getCurrentPaymentScheduleId() != null
                || subscription.getCurrentPeriodEnd() == null) {
            return Optional.empty();
        }

        SubscriptionPlan nextBillingPlan = subscription.getNextBillingPlan();
        LocalDateTime nextPeriodStart = subscription.getCurrentPeriodEnd();
        LocalDateTime nextPeriodEnd = calculateNextPeriodEnd(nextPeriodStart, nextBillingPlan.getBillingCycle());

        Invoice invoice = invoiceRepository.save(Invoice.create(
                subscription,
                nextBillingPlan.getPrice(),
                nextBillingPlan.getCurrency(),
                nextPeriodStart,
                nextPeriodEnd
        ));
        Payment payment = paymentRepository.save(Payment.create(
                invoice,
                nextBillingPlan.getPrice(),
                nextBillingPlan.getCurrency()
        ));

        Instant timeToPay = nextPeriodStart.atZone(ZoneId.of("UTC")).toInstant();
        return Optional.of(new NextScheduleAttempt(
                invoice.getId(),
                payment.getId(),
                nextBillingPlan.getProduct().getName() + " - 갱신",
                nextBillingPlan.getCurrency(),
                nextBillingPlan.getPrice(),
                timeToPay,
                nextPeriodStart
        ));
    }

    @Transactional
    public void completeScheduleCreation(Long subscriptionId, String newScheduleId) {
        subscriptionRepository.findById(subscriptionId)
                .ifPresent(subscription -> subscription.setCurrentPaymentScheduleId(newScheduleId));
        cancelPendingScheduleOutboxIfPresent(subscriptionId);
    }

    @Transactional
    public void abortFailedScheduleAttempt(String invoiceId, String paymentId, String reason) {
        if (paymentId != null) {
            paymentRepository.findById(paymentId)
                    .ifPresent(payment -> payment.markAsFailed(reason));
        }
        if (invoiceId != null) {
            invoiceRepository.findById(invoiceId)
                    .ifPresent(Invoice::markAsFailed);
        }
    }

    Subscription getValidPaidActiveSubscriptionForPlanChange(UUID userId) {
        Subscription subscription = currentSubscriptionService.getCurrentSubscription(userId);
        if (!subscription.isActive()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "활성 상태의 구독만 플랜을 변경할 수 있습니다.");
        }
        if (subscription.getSubscriptionPlan().isFree()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "무료 플랜에서는 신규 유료 구독 생성을 이용해주세요.");
        }
        return subscription;
    }

    SubscriptionPlan getValidTargetPaidPlan(Integer targetPlanId, SubscriptionPlan currentPlan) {
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

    Optional<PaymentMethod> resolveActivePaymentMethodForCharge(UUID userId, Subscription subscription) {
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

    PaymentMethod validateAndGetPaymentMethod(UUID userId, Long paymentMethodId) {
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

    private void assignFreePlanIfNoCurrent(UUID userId) {
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

    private void enqueueScheduleCreateOutbox(Long subscriptionId, String billingKey) {
        cancelPendingScheduleOutboxIfPresent(subscriptionId);
        outboxEventRepository.save(OutboxEvent.scheduleCreate(subscriptionId, billingKey));
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
