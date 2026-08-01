package app.mockly.domain.payment.scheduler;

import app.mockly.domain.auth.entity.User;
import app.mockly.domain.payment.entity.OutboxEvent;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
import app.mockly.domain.product.entity.Subscription;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.domain.product.service.SubscriptionService;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ScheduleCreationHandlerTest {

    @Mock
    private PaymentMethodRepository paymentMethodRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionService subscriptionService;

    private ScheduleCreationHandler handler;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        handler = new ScheduleCreationHandler(
                paymentMethodRepository,
                subscriptionRepository,
                subscriptionService,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("잘못된 payload는 영구 오류로 분류한다")
    void malformedPayloadIsPermanentFailure() {
        OutboxEvent event = OutboxEvent.createSchedule(1L, "not-json");

        assertFailureCode(event, "INVALID_PAYLOAD");
    }

    @Test
    @DisplayName("결제수단 ID가 누락된 payload는 영구 오류로 분류한다")
    void missingPaymentMethodIdIsPermanentFailure() {
        OutboxEvent event = OutboxEvent.createSchedule(1L, "{}");

        assertFailureCode(event, "INVALID_PAYLOAD");
    }

    @Test
    @DisplayName("존재하지 않는 결제수단은 영구 오류로 분류한다")
    void missingPaymentMethodIsPermanentFailure() {
        OutboxEvent event = pendingEvent();
        given(paymentMethodRepository.findById(20L)).willReturn(Optional.empty());

        assertFailureCode(event, "PAYMENT_METHOD_NOT_FOUND");
    }

    @Test
    @DisplayName("결제수단 소유자 불일치는 영구 오류로 분류한다")
    void paymentMethodOwnerMismatchIsPermanentFailure() {
        OutboxEvent event = pendingEvent();
        PaymentMethod paymentMethod = mock(PaymentMethod.class);
        User owner = mock(User.class);
        given(paymentMethodRepository.findById(20L)).willReturn(Optional.of(paymentMethod));
        given(paymentMethod.getUser()).willReturn(owner);
        given(owner.getId()).willReturn(UUID.randomUUID());
        given(subscription.getUserId()).willReturn(UUID.randomUUID());

        assertFailureCode(event, "PAYMENT_METHOD_OWNER_MISMATCH");
    }

    @Test
    @DisplayName("비활성 결제수단은 영구 오류로 분류한다")
    void inactivePaymentMethodIsPermanentFailure() {
        OutboxEvent event = pendingEvent();
        PaymentMethod paymentMethod = paymentMethodOwnedBySubscription();
        given(paymentMethod.isActive()).willReturn(false);

        assertFailureCode(event, "PAYMENT_METHOD_INACTIVE");
    }

    @Test
    @DisplayName("중복 PortOne 스케줄은 영구 오류로 분류한다")
    void duplicateScheduleIsPermanentFailure() {
        OutboxEvent event = pendingEvent();
        PaymentMethod paymentMethod = paymentMethodOwnedBySubscription();
        given(paymentMethod.isActive()).willReturn(true);
        given(paymentMethod.getBillingKey()).willReturn("billing-key");
        willThrow(new BusinessException(ApiStatusCode.DUPLICATE_RESOURCE, "중복 스케줄"))
                .given(subscriptionService).createFirstPaymentSchedule(subscription, "billing-key");

        assertFailureCode(event, "PAYMENT_SCHEDULE_ALREADY_EXISTS");
    }

    private OutboxEvent pendingEvent() {
        OutboxEvent event = OutboxEvent.createSchedule(1L, "{\"paymentMethodId\":20}");
        subscription = mock(Subscription.class);
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));
        given(subscription.getCurrentPaymentScheduleId()).willReturn(null);
        return event;
    }

    private PaymentMethod paymentMethodOwnedBySubscription() {
        UUID userId = UUID.randomUUID();
        PaymentMethod paymentMethod = mock(PaymentMethod.class);
        User owner = mock(User.class);
        given(paymentMethodRepository.findById(20L)).willReturn(Optional.of(paymentMethod));
        given(paymentMethod.getUser()).willReturn(owner);
        given(owner.getId()).willReturn(userId);
        given(subscription.getUserId()).willReturn(userId);
        return paymentMethod;
    }

    private void assertFailureCode(OutboxEvent event, String expectedCode) {
        assertThatThrownBy(() -> handler.handle(event))
                .isInstanceOfSatisfying(NonRetryableOutboxException.class,
                        exception -> assertThat(exception.getFailureCode()).isEqualTo(expectedCode));
    }
}
