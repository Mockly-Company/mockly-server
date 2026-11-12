package app.mockly.domain.product.service;

import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.entity.*;
import app.mockly.domain.payment.repository.InvoiceRepository;
import app.mockly.domain.payment.repository.OutboxEventRepository;
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
import app.mockly.domain.product.service.SubscriptionBillingStateService.CancelStateResult;
import app.mockly.domain.product.service.SubscriptionBillingStateService.NextScheduleAttempt;
import app.mockly.domain.product.service.SubscriptionBillingStateService.PaymentMethodUpdatePlan;
import app.mockly.domain.product.service.SubscriptionBillingStateService.PlanChangePlan;
import app.mockly.domain.product.service.SubscriptionBillingStateService.PlanUpgradeCompletionResult;
import app.mockly.domain.product.service.SubscriptionBillingStateService.ScheduleSyncTarget;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import app.mockly.global.security.SensitiveDataMasker;
import io.portone.sdk.server.common.PaymentAmountInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {
    private final PortOneService portOneService;
    private final PaymentScheduleService paymentScheduleService;
    private final SubscriptionBillingStateService billingStateService;

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

        PaymentMethod paymentMethod = billingStateService.validateAndGetPaymentMethod(userId, paymentMethodId);
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

            payment.markAsFailed("SUBSCRIPTION_CREATE_PAYMENT_FAILED");
            invoice.markAsFailed();

            throw new BusinessException(ApiStatusCode.INTERNAL_SERVER_ERROR, "결제 처리 중 오류가 발생했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public GetSubscriptionResponse getMySubscription(UUID userId) {
        Subscription subscription = currentSubscriptionService.getCurrentSubscription(userId);
        PaymentMethod paymentMethod = resolvePaymentMethodForDisplay(userId, subscription).orElse(null);
        return GetSubscriptionResponse.from(subscription, paymentMethod);
    }

    public CancelSubscriptionResponse cancelSubscription(UUID userId, Long subscriptionId) {
        return cancelSubscription(userId, subscriptionId, false);
    }

    public CancelSubscriptionResponse cancelSubscription(UUID userId, Long subscriptionId, boolean immediate) {
        CancelStateResult stateResult = billingStateService.cancelSubscriptionState(userId, subscriptionId, immediate);
        revokeScheduleOutsideTx(subscriptionId, stateResult.oldScheduleId());
        return stateResult.response();
    }

    public void completeExpiredCancelAtPeriodEndSubscription(Long subscriptionId, LocalDateTime now) {
        billingStateService.transitionExpiredCancelAtPeriodEnd(subscriptionId, now)
                .ifPresent(result -> revokeScheduleOutsideTx(subscriptionId, result.oldScheduleId()));
    }

    @Transactional
    public void assignFreePlan(UUID userId) {
        billingStateService.assignFreePlan(userId);
    }

    public void updatePaymentMethod(UUID userId, Long subscriptionId, Long newPaymentMethodId) {
        PaymentMethodUpdatePlan plan = billingStateService.preparePaymentMethodUpdate(
                userId,
                subscriptionId,
                newPaymentMethodId
        );

        if (plan instanceof PaymentMethodUpdatePlan.Completed) {
            return;
        }

        if (plan instanceof PaymentMethodUpdatePlan.ScheduleSyncOnly syncOnly) {
            syncNextBillingScheduleOutsideTx(
                    syncOnly.subscriptionId(),
                    syncOnly.oldScheduleId(),
                    syncOnly.billingKey()
            );
            return;
        }

        if (plan instanceof PaymentMethodUpdatePlan.DelinquentRecovery recovery) {
            try {
                PaymentAmountInput amountInput = new PaymentAmountInput(recovery.amount().longValue(), null, null);
                portOneService.payWithBillingKey(
                        recovery.paymentId(),
                        recovery.billingKey(),
                        recovery.orderName(),
                        recovery.currency().toPortOneCurrency(),
                        amountInput
                );
            } catch (Exception e) {
                log.error("연체 복구 결제 실패 - subscriptionId: {}, paymentId: {}, billingKey: {}",
                        recovery.subscriptionId(),
                        recovery.paymentId(),
                        SensitiveDataMasker.maskBillingKey(recovery.billingKey()),
                        e);
                billingStateService.markChargeFailed(
                        recovery.invoiceId(),
                        recovery.paymentId(),
                        "DELINQUENT_RECOVERY_PAYMENT_FAILED"
                );
                throw new BusinessException(ApiStatusCode.INTERNAL_SERVER_ERROR, "미납 결제 승인 중 오류가 발생했습니다.");
            }

            ScheduleSyncTarget syncTarget = billingStateService.completeDelinquentRecovery(userId, recovery);
            syncNextBillingScheduleOutsideTx(
                    syncTarget.subscriptionId(),
                    syncTarget.oldScheduleId(),
                    syncTarget.billingKey()
            );

            log.info("연체 구독 복구 완료 - subscriptionId: {}, paymentId: {}, paymentMethodId: {}",
                    recovery.subscriptionId(), recovery.paymentId(), recovery.newPaymentMethodId());
        }
    }

    @Transactional(readOnly = true)
    public GetSubscriptionPlanChangeResponse previewPlanChange(UUID userId, Integer targetPlanId) {
        if (targetPlanId == null) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "대상 플랜 ID(targetPlanId)는 필수입니다.");
        }

        Subscription subscription = billingStateService.getValidPaidActiveSubscriptionForPlanChange(userId);
        SubscriptionPlan currentPlan = subscription.getSubscriptionPlan();
        SubscriptionPlan targetPlan = billingStateService.getValidTargetPaidPlan(targetPlanId, currentPlan);

        LocalDateTime now = LocalDateTime.now();
        BigDecimal immediateAmount = billingStateService.calculateProratedImmediateAmount(
                subscription,
                currentPlan,
                targetPlan,
                now
        );
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

    public CreateSubscriptionPlanChangeResponse changeSubscriptionPlan(
            UUID userId,
            CreateSubscriptionPlanChangeRequest request
    ) {
        PlanChangePlan plan = billingStateService.preparePlanChange(userId, request);

        if (plan instanceof PlanChangePlan.DowngradeCompleted downgrade) {
            syncNextBillingScheduleOutsideTx(
                    downgrade.subscriptionId(),
                    downgrade.oldScheduleId(),
                    downgrade.billingKey()
            );
            return downgrade.response();
        }

        PlanChangePlan.UpgradeChargeRequired upgrade = (PlanChangePlan.UpgradeChargeRequired) plan;
        if (upgrade.immediateAmount().compareTo(BigDecimal.ZERO) > 0) {
            try {
                PaymentAmountInput amountInput = new PaymentAmountInput(upgrade.immediateAmount().longValue(), null, null);
                portOneService.payWithBillingKey(
                        upgrade.paymentId(),
                        upgrade.billingKey(),
                        upgrade.orderName(),
                        upgrade.currency().toPortOneCurrency(),
                        amountInput
                );
            } catch (Exception e) {
                log.error("플랜 업그레이드 차액 결제 실패 - subscriptionId: {}, paymentId: {}, billingKey: {}",
                        upgrade.subscriptionId(),
                        upgrade.paymentId(),
                        SensitiveDataMasker.maskBillingKey(upgrade.billingKey()),
                        e);
                billingStateService.markChargeFailed(
                        upgrade.invoiceId(),
                        upgrade.paymentId(),
                        "PLAN_UPGRADE_PAYMENT_FAILED"
                );
                throw new BusinessException(ApiStatusCode.INTERNAL_SERVER_ERROR, "플랜 변경 차액 결제 중 오류가 발생했습니다.");
            }
        }

        PlanUpgradeCompletionResult completion = billingStateService.completePlanUpgrade(userId, upgrade);
        syncNextBillingScheduleOutsideTx(
                upgrade.subscriptionId(),
                completion.oldScheduleId(),
                completion.billingKey()
        );
        return completion.response();
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

    BigDecimal calculateProratedImmediateAmount(
            Subscription subscription,
            SubscriptionPlan currentPlan,
            SubscriptionPlan targetPlan,
            LocalDateTime now
    ) {
        return billingStateService.calculateProratedImmediateAmount(subscription, currentPlan, targetPlan, now);
    }

    private void syncNextBillingScheduleOutsideTx(Long subscriptionId, String oldScheduleId, String billingKey) {
        revokeScheduleOutsideTx(subscriptionId, oldScheduleId);
        if (billingKey == null) {
            return;
        }

        Optional<NextScheduleAttempt> attemptOpt = billingStateService.prepareNextScheduleAttempt(subscriptionId);
        if (attemptOpt.isEmpty()) {
            return;
        }

        NextScheduleAttempt attempt = attemptOpt.get();
        try {
            String newScheduleId = portOneService.createPaymentSchedule(
                    attempt.paymentId(),
                    billingKey,
                    attempt.orderName(),
                    attempt.currency().toPortOneCurrency(),
                    new PaymentAmountInput(attempt.amount().longValue(), null, null),
                    attempt.timeToPay()
            );
            billingStateService.completeScheduleCreation(subscriptionId, newScheduleId);
            log.info("결제 스케줄 동기화 완료 - subscriptionId: {}, scheduleId: {}, billingKey: {}, 다음 결제일: {}",
                    subscriptionId,
                    newScheduleId,
                    SensitiveDataMasker.maskBillingKey(billingKey),
                    attempt.nextPeriodStart());
        } catch (Exception e) {
            log.warn("결제 스케줄 즉시 생성 실패 (Outbox 재시도 대기) - subscriptionId: {}, billingKey: {}",
                    subscriptionId,
                    SensitiveDataMasker.maskBillingKey(billingKey),
                    e);
            billingStateService.abortFailedScheduleAttempt(
                    attempt.invoiceId(),
                    attempt.paymentId(),
                    "PORTONE_SCHEDULE_CREATE_DEFERRED_TO_OUTBOX"
            );
        }
    }

    private void revokeScheduleOutsideTx(Long subscriptionId, String oldScheduleId) {
        if (oldScheduleId == null) {
            return;
        }
        try {
            portOneService.revokePaymentSchedule(oldScheduleId);
            log.info("결제 스케줄 취소 완료 - subscriptionId: {}, scheduleId: {}",
                    subscriptionId, oldScheduleId);
        } catch (Exception e) {
            log.error("결제 스케줄 취소 실패 - subscriptionId: {}, scheduleId: {}",
                    subscriptionId, oldScheduleId, e);
        }
    }

    private Optional<PaymentMethod> resolvePaymentMethodForDisplay(UUID userId, Subscription subscription) {
        if (subscription.getSubscriptionPlan().isFree()) {
            return Optional.empty();
        }
        return billingStateService.resolveActivePaymentMethodForCharge(userId, subscription);
    }

    private void lockUser(UUID userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));
    }

    private LocalDateTime calculateNextPeriodEnd(LocalDateTime start, BillingCycle billingCycle) {
        return switch (billingCycle) {
            case MONTHLY -> start.plusMonths(1);
            case YEARLY -> start.plusYears(1);
            case LIFETIME -> null;
        };
    }
}
