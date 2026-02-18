package app.mockly.domain.payment.service;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.dto.response.PaymentMethodResponse;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
import app.mockly.domain.product.entity.SubscriptionStatus;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import io.portone.sdk.server.common.Card;
import io.portone.sdk.server.payment.billingkey.BillingKeyInfo;
import io.portone.sdk.server.payment.billingkey.BillingKeyPaymentMethod;
import io.portone.sdk.server.payment.billingkey.BillingKeyPaymentMethodCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentMethodService {
    private final PaymentMethodRepository paymentMethodRepository;
    private final PortOneService portOneService;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public PaymentMethodResponse addPaymentMethod(UUID userId, String billingKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        BillingKeyInfo billingKeyInfo = portOneService.getBillingKey(billingKey);
        Card card = getCard(billingKeyInfo);
        String cardNumber = card.getNumber();
        String cardBrand = card.getBrand().toString();

        List<PaymentMethod> existingMethods = paymentMethodRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId);
        boolean isDefault = existingMethods.isEmpty();

        PaymentMethod paymentMethod = PaymentMethod.create(user, billingKey, cardNumber, cardBrand, isDefault);
        PaymentMethod saved = paymentMethodRepository.save(paymentMethod);

        log.info("결제 수단 추가 - userId: {}, paymentMethodId: {}, cardBrand: {}, isDefault: {}",
                userId, saved.getId(), cardBrand, isDefault);

        return PaymentMethodResponse.from(saved);
    }

    public List<PaymentMethodResponse> getPaymentMethods(UUID userId) {
        List<PaymentMethod> paymentMethods = paymentMethodRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId);
        return paymentMethods.stream()
                .map(PaymentMethodResponse::from)
                .toList();
    }

    @Transactional
    public void deletePaymentMethod(UUID userId, Long paymentMethodId) {
        PaymentMethod paymentMethod = paymentMethodRepository.findByIdAndUserIdAndIsActiveTrue(paymentMethodId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "결제 수단을 찾을 수 없습니다."));

        boolean hasActiveSubscription = subscriptionRepository.existsByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        if (hasActiveSubscription && paymentMethod.isDefault()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST,
                    "활성 구독에 사용 중인 기본 결제 수단입니다. 구독을 해지하거나 다른 결제 수단을 기본으로 설정 후 삭제해주세요.");
        }

        paymentMethod.deactivate();
        paymentMethodRepository.save(paymentMethod);

        log.info("결제 수단 삭제 - userId: {}, paymentMethodId: {}, hadActiveSubscription: {}",
                userId, paymentMethodId, hasActiveSubscription);
    }

    @Transactional
    public PaymentMethodResponse setDefaultPaymentMethod(UUID userId, Long paymentMethodId) {
        PaymentMethod paymentMethod = paymentMethodRepository.findByIdAndUserIdAndIsActiveTrue(paymentMethodId, userId)
                .orElseThrow(() -> new BusinessException(ApiStatusCode.RESOURCE_NOT_FOUND, "결제 수단을 찾을 수 없습니다."));
        if (paymentMethod.isDefault()) {
            return PaymentMethodResponse.from(paymentMethod);
        }

        Long oldDefaultId = paymentMethodRepository.findByUserIdAndIsDefaultTrueAndIsActiveTrue(userId)
                .map(currentDefault -> {
                    currentDefault.unsetDefault();
                    paymentMethodRepository.save(currentDefault);
                    return currentDefault.getId();
                })
                .orElse(null);
        paymentMethod.setAsDefault();
        PaymentMethod saved = paymentMethodRepository.save(paymentMethod);

        log.info("기본 결제 수단 변경 - userId: {}, oldDefaultId: {}, newDefaultId: {}", userId, oldDefaultId, paymentMethodId);
        return PaymentMethodResponse.from(saved);
    }

    private static Card getCard(BillingKeyInfo billingKeyInfo) {
        if (!(billingKeyInfo instanceof BillingKeyInfo.Recognized recognized)) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "유효하지 않은 빌링키입니다.");
        }

        List<BillingKeyPaymentMethod> methods = recognized.getMethods();
        if (methods == null || methods.isEmpty()) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "결제 수단 정보를 찾을 수 없습니다.");
        }

        BillingKeyPaymentMethod method = methods.getFirst();
        if (!(method instanceof BillingKeyPaymentMethodCard cardMethod)) {
            throw new BusinessException(ApiStatusCode.BAD_REQUEST, "현재 카드 결제 수단만 지원합니다.");
        }

        return cardMethod.getCard();
    }
}
