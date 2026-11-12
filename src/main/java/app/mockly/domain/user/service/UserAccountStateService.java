package app.mockly.domain.user.service;

import app.mockly.domain.auth.entity.RefreshToken;
import app.mockly.domain.auth.entity.Session;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.RefreshTokenRepository;
import app.mockly.domain.auth.repository.SessionRepository;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAccountStateService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    public record WithdrawalStateResult(
            Long canceledSubscriptionId,
            String scheduleIdToRevoke
    ) {
    }

    @Transactional
    public WithdrawalStateResult withdrawUserInTransaction(UUID authenticatedUserId, UUID targetUserId) {
        if (authenticatedUserId == null || !authenticatedUserId.equals(targetUserId)) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "본인 계정만 탈퇴할 수 있습니다.");
        }

        User user = userRepository.findByIdForUpdate(targetUserId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));

        final Long[] subscriptionIdHolder = {null};
        final String[] scheduleIdHolder = {null};
        subscriptionRepository.findCurrentByUserId(targetUserId).ifPresent(subscription -> {
            subscriptionIdHolder[0] = subscription.getId();
            scheduleIdHolder[0] = subscription.getCurrentPaymentScheduleId();
            subscription.cancel();
            subscriptionRepository.saveAndFlush(subscription);
        });

        List<PaymentMethod> activeMethods = paymentMethodRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(targetUserId);
        for (PaymentMethod method : activeMethods) {
            method.unsetDefault();
            method.deactivate();
        }

        List<Session> sessions = sessionRepository.findByUserIdWithRefreshToken(targetUserId);
        List<RefreshToken> tokensToDelete = new ArrayList<>();
        for (Session session : sessions) {
            RefreshToken refreshToken = session.getRefreshToken();
            if (refreshToken != null) {
                session.updateRefreshToken(null);
                tokensToDelete.add(refreshToken);
            }
        }
        if (!tokensToDelete.isEmpty()) {
            refreshTokenRepository.deleteAll(tokensToDelete);
        }
        if (Hibernate.isInitialized(user.getSessions())) {
            user.getSessions().clear();
        }
        if (!sessions.isEmpty()) {
            sessionRepository.deleteAll(sessions);
        }

        user.withdraw(Instant.now());
        return new WithdrawalStateResult(subscriptionIdHolder[0], scheduleIdHolder[0]);
    }
}
