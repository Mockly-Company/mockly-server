package app.mockly.domain.product.service;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.entity.Invoice;
import app.mockly.domain.payment.entity.Payment;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.entity.PaymentMethodType;
import app.mockly.domain.payment.repository.InvoiceRepository;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
import app.mockly.domain.payment.repository.PaymentRepository;
import app.mockly.domain.product.dto.request.CreateSubscriptionRequest;
import app.mockly.domain.product.dto.response.CancelSubscriptionResponse;
import app.mockly.domain.product.dto.response.CreateSubscriptionResponse;
import app.mockly.domain.product.dto.response.GetSubscriptionResponse;
import app.mockly.domain.product.entity.*;
import app.mockly.domain.product.repository.SubscriptionPlanRepository;
import app.mockly.domain.product.repository.SubscriptionProductRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import io.portone.sdk.server.common.Card;
import io.portone.sdk.server.common.CardBrand;
import io.portone.sdk.server.common.PaymentAmountInput;
import io.portone.sdk.server.payment.PayWithBillingKeyResponse;
import io.portone.sdk.server.payment.billingkey.BillingKeyInfo;
import io.portone.sdk.server.payment.billingkey.BillingKeyPaymentMethod;
import io.portone.sdk.server.payment.billingkey.BillingKeyPaymentMethodCard;
import io.portone.sdk.server.payment.billingkey.BillingKeyPaymentMethodEasyPay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {
    private final PaymentMethodRepository paymentMethodRepository;
    private final PortOneService portOneService;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionProductRepository subscriptionProductRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    @Transactional
    public CreateSubscriptionResponse createSubscription(UUID userId, CreateSubscriptionRequest request) {
        Integer planId = request.planId();
        BigDecimal expectedPrice = request.expectedPrice();
        String billingKey = request.billingKey();

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
        Invoice invoice = Invoice.create(subscription, subscriptionPlan.getPrice(), Currency.KRW, now, periodEnd);
        invoiceRepository.save(invoice);

        Payment payment = Payment.create(invoice, subscriptionPlan.getPrice(), subscriptionPlan.getCurrency());
        paymentRepository.save(payment);

        // Billing Key 조회 및 검증
        BillingKeyInfo billingKeyInfo = portOneService.getBillingKey(billingKey);

        // 결제 요청
        try {
            PaymentAmountInput paymentAmountInput = new PaymentAmountInput(subscriptionPlan.getPrice().longValue(), null, null);
            String orderName = subscriptionPlan.getProduct().getName() + " - " + subscriptionPlan.getBillingCycle().name();
            PayWithBillingKeyResponse response = portOneService.payWithBillingKey(
                    payment.getId(), billingKey, orderName, subscriptionPlan.getCurrency().toPortOneCurrency(), paymentAmountInput);

            if (!(billingKeyInfo instanceof BillingKeyInfo.Recognized recognized)) {
                throw new BusinessException(ApiStatusCode.BAD_REQUEST, "유효하지 않은 빌링키입니다.");
            }

            // 결제 수단 정보 추출
            PaymentMethodType paymentMethodType = PaymentMethodType.UNKNOWN;
            String cardNumber = null;
            String cardBrand = null;
            List<BillingKeyPaymentMethod> methods = recognized.getMethods();
            if (methods != null && !methods.isEmpty()) {
                BillingKeyPaymentMethod billingKeyPaymentMethod = methods.getFirst();
                if (billingKeyPaymentMethod instanceof BillingKeyPaymentMethodCard cardMethod) {
                    paymentMethodType = PaymentMethodType.CARD;

                    Card card = cardMethod.getCard();
                    cardNumber = card.getNumber();
                    CardBrand brand = card.getBrand();
                    if (brand != null) {
                        cardBrand = brand.toString();
                    }
                } else if (billingKeyPaymentMethod instanceof BillingKeyPaymentMethodEasyPay easyPayMethod) {
                    paymentMethodType = PaymentMethodType.EASY_PAY;
                    // TODO: EasyPay 정보 추출 (필요 시)
                }
            }
            payment.markAsPaid(paymentMethodType);
            invoice.markAsPaid();
            subscription.activate();

            // PaymentMethod 저장 or 업데이트
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));

            PaymentMethod activePaymentMethod = paymentMethodRepository.findByUserIdAndIsActiveTrue(userId)
                    .orElse(null);

            if (activePaymentMethod != null) {
                if (activePaymentMethod.getBillingKey().equals(billingKey)) {
                    return CreateSubscriptionResponse.from(subscription);
                }
                activePaymentMethod.deactivate();
            }
            PaymentMethod newPaymentMethod = PaymentMethod.create(user, billingKey, cardNumber, cardBrand, true);
            paymentMethodRepository.save(newPaymentMethod);

            log.info("구독 생성 성공 - userId: {}, subscriptionId: {}, paymentId: {}", userId, subscription.getId(), payment.getId());

            return CreateSubscriptionResponse.from(subscription);
        } catch (Exception e) {
            log.error("구독 생성 실패 - userId: {}, subscriptionId: {}, paymentId: {}", userId, subscription.getId(), payment.getId());

            payment.markAsFailed(e.getMessage());
            invoice.markAsFailed();

            throw new BusinessException(ApiStatusCode.INTERNAL_SERVER_ERROR, "결제 처리 중 오류가 발생했습니다."); // TODO: Api Status Code 추가하기 or Exception 추가하기
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

    /**
     * 첫 결제 후 스케줄 생성 - Webhook에서 호출
     * @param subscription 구독 정보
     * @param billingKey 첫 결제에 사용된 빌링키 (갱신 결제에도 동일한 키 사용)
     */
    @Transactional
    public void createFirstPaymentSchedule(Subscription subscription, String billingKey) {
        try {
            LocalDateTime nextPeriodStart = subscription.getCurrentPeriodEnd();
            LocalDateTime nextPeriodEnd = calculateNextPeriodEnd(
                    nextPeriodStart,
                    subscription.getSubscriptionPlan().getBillingCycle()
            );

            Invoice nextInvoice = Invoice.create(
                    subscription,
                    subscription.getSubscriptionPlan().getPrice(),
                    Currency.KRW,
                    nextPeriodStart,
                    nextPeriodEnd
            );
            invoiceRepository.save(nextInvoice);

            Payment nextPayment = Payment.create(
                    nextInvoice,
                    subscription.getSubscriptionPlan().getPrice(),
                    subscription.getSubscriptionPlan().getCurrency()
            );
            paymentRepository.save(nextPayment);

            // 다음 결제 예약 시점 = 다음 기간의 시작일
            LocalDateTime nextPaymentTime = nextPeriodStart;
            java.time.Instant timeToPay = nextPaymentTime.atZone(java.time.ZoneId.systemDefault()).toInstant();

            // 스케줄 생성: 첫 결제 시 사용한 billingKey 사용
            String scheduleId = portOneService.createPaymentSchedule(
                    nextPayment.getId(),
                    billingKey,
                    subscription.getSubscriptionPlan().getProduct().getName() + " - 갱신",
                    subscription.getSubscriptionPlan().getCurrency().toPortOneCurrency(),
                    new PaymentAmountInput(subscription.getSubscriptionPlan().getPrice().longValue(), null, null),
                    timeToPay
            );

            subscription.setCurrentPaymentScheduleId(scheduleId);
            log.info("첫 결제 스케줄 생성 완료 - subscriptionId: {}, billingKey: {}, 다음 결제일: {}",
                    subscription.getId(), billingKey, nextPaymentTime);

        } catch (Exception e) {
            log.error("첫 결제 스케줄 생성 실패 - subscriptionId: {}, billingKey: {}", subscription.getId(), billingKey, e);
            // TODO: 재시도 로직 추가
        }
    }

    /**
     * 구독 갱신 - Webhook에서 호출
     * @param subscription 구독 정보
     * @param billingKey 갱신 결제에 사용된 빌링키 (다음 스케줄에도 동일한 키 사용)
     */
    @Transactional
    public void renewSubscription(Subscription subscription, String billingKey) {
        try {
            subscription.extendPeriod();

            LocalDateTime nextPeriodStart = subscription.getCurrentPeriodEnd();
            LocalDateTime nextPeriodEnd = calculateNextPeriodEnd(
                    nextPeriodStart,
                    subscription.getSubscriptionPlan().getBillingCycle()
            );

            Invoice nextInvoice = Invoice.create(
                    subscription,
                    subscription.getSubscriptionPlan().getPrice(),
                    Currency.KRW,
                    nextPeriodStart,
                    nextPeriodEnd
            );
            invoiceRepository.save(nextInvoice);

            Payment nextPayment = Payment.create(
                    nextInvoice,
                    subscription.getSubscriptionPlan().getPrice(),
                    subscription.getSubscriptionPlan().getCurrency()
            );
            paymentRepository.save(nextPayment);

            // 다음 결제 스케줄 생성: 다음 기간 시작일에 결제
            LocalDateTime nextPaymentTime = nextPeriodStart;
            java.time.Instant timeToPay = nextPaymentTime.atZone(java.time.ZoneId.systemDefault()).toInstant();

            String scheduleId = portOneService.createPaymentSchedule(
                    nextPayment.getId(),
                    billingKey,
                    subscription.getSubscriptionPlan().getProduct().getName() + " - 갱신",
                    subscription.getSubscriptionPlan().getCurrency().toPortOneCurrency(),
                    new PaymentAmountInput(subscription.getSubscriptionPlan().getPrice().longValue(), null, null),
                    timeToPay
            );

            subscription.setCurrentPaymentScheduleId(scheduleId);
            log.info("구독 갱신 완료 - subscriptionId: {}, billingKey: {}, 현재 기간: {} ~ {}, 다음 결제일: {}",
                    subscription.getId(),
                    billingKey,
                    subscription.getCurrentPeriodStart(),
                    subscription.getCurrentPeriodEnd(),
                    nextPaymentTime);

        } catch (Exception e) {
            log.error("구독 갱신 실패 - subscriptionId: {}, billingKey: {}", subscription.getId(), billingKey, e);
            // TODO: 재시도 로직 추가
        }
    }

    /**
     * 다음 기간 종료일 계산
     */
    private LocalDateTime calculateNextPeriodEnd(LocalDateTime start, BillingCycle billingCycle) {
        return switch (billingCycle) {
            case MONTHLY -> start.plusMonths(1);
            case YEARLY -> start.plusYears(1);
            case LIFETIME -> null;
        };
    }
}
