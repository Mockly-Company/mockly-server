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
            String cardLast4 = null;
            String cardBrand = null;
            List<BillingKeyPaymentMethod> methods = recognized.getMethods();
            if (methods != null && !methods.isEmpty()) {
                BillingKeyPaymentMethod billingKeyPaymentMethod = methods.getFirst();
                if (billingKeyPaymentMethod instanceof BillingKeyPaymentMethodCard cardMethod) {
                    paymentMethodType = PaymentMethodType.CARD;

                    Card card = cardMethod.getCard();
                    String cardNumber = card.getNumber();
                    if (cardNumber != null && cardNumber.length() >= 4) {
                        cardLast4 = cardNumber.substring(cardNumber.length() - 4);
                    }
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
            PaymentMethod newPaymentMethod = PaymentMethod.create(user, billingKey, cardLast4, cardBrand);
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

        subscription.cancel();
        subscriptionRepository.save(subscription);

        // TODO: PortOne 구독 예약 스케줄링 해지

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
}
