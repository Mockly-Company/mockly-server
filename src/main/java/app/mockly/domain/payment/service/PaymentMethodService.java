package app.mockly.domain.payment.service;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.dto.response.PaymentMethodResponse;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
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
