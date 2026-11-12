package app.mockly.domain.user.service;

import app.mockly.domain.auth.dto.UserInfo;
import app.mockly.domain.auth.entity.RefreshToken;
import app.mockly.domain.auth.entity.Session;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.RefreshTokenRepository;
import app.mockly.domain.auth.repository.SessionRepository;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.payment.entity.PaymentMethod;
import app.mockly.domain.payment.repository.PaymentMethodRepository;
import app.mockly.domain.product.repository.SubscriptionRepository;
import app.mockly.domain.user.dto.request.SaveUserAgreementsRequest;
import app.mockly.domain.user.dto.request.UpdateUserProfileRequest;
import app.mockly.domain.user.entity.AgreementType;
import app.mockly.domain.user.entity.UserAgreement;
import app.mockly.domain.user.repository.UserAgreementRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final UserAgreementRepository userAgreementRepository;
    private final SessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PortOneService portOneService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public void saveUserAgreements(UUID userId, SaveUserAgreementsRequest request) {
        User user = findActiveUser(userId);

        Map<AgreementType, Boolean> submitted = new EnumMap<>(AgreementType.class);
        for (SaveUserAgreementsRequest.AgreementItem item : request.agreements()) {
            AgreementType type = AgreementType.fromCode(item.type());
            submitted.put(type, item.agreed());
        }

        for (AgreementType requiredType : AgreementType.requiredTypes()) {
            if (!Boolean.TRUE.equals(submitted.get(requiredType))) {
                throw new BusinessException(
                        ApiStatusCode.VALIDATION_ERROR,
                        "필수 약관(만 14세 이상, 서비스 이용약관, 개인정보 수집·이용)에 모두 동의해야 합니다."
                );
            }
        }

        Instant now = Instant.now();
        Map<AgreementType, UserAgreement> existingByType = userAgreementRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(UserAgreement::getAgreementType, Function.identity()));

        for (Map.Entry<AgreementType, Boolean> entry : submitted.entrySet()) {
            AgreementType type = entry.getKey();
            boolean agreed = Boolean.TRUE.equals(entry.getValue());
            UserAgreement existing = existingByType.get(type);
            if (existing != null) {
                existing.updateAgreement(agreed, now);
            } else {
                userAgreementRepository.save(UserAgreement.create(user, type, agreed, now));
            }
        }

        user.agreeToTerms(now);
    }

    @Transactional
    public UserInfo updateMyProfile(UUID userId, UpdateUserProfileRequest request) {
        User user = findActiveUser(userId);
        String trimmedName = request.name() != null ? request.name().trim() : "";
        if (trimmedName.isEmpty() || trimmedName.length() > 50) {
            throw new BusinessException(ApiStatusCode.VALIDATION_ERROR, "닉네임은 1자 이상 50자 이하여야 합니다.");
        }

        user.updateProfile(
                trimmedName,
                request.jobCategory(),
                request.jobRole(),
                request.experienceYears()
        );
        return UserInfo.from(user);
    }

    @Transactional
    public void withdrawUser(UUID authenticatedUserId, UUID targetUserId, String accessToken) {
        if (authenticatedUserId == null || !authenticatedUserId.equals(targetUserId)) {
            throw new BusinessException(ApiStatusCode.FORBIDDEN, "본인 계정만 탈퇴할 수 있습니다.");
        }

        User user = userRepository.findByIdForUpdate(targetUserId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));

        subscriptionRepository.findCurrentByUserId(targetUserId).ifPresent(subscription -> {
            String scheduleId = subscription.getCurrentPaymentScheduleId();
            if (scheduleId != null) {
                try {
                    portOneService.revokePaymentSchedule(scheduleId);
                    log.info("회원 탈퇴 결제 스케줄 취소 완료 - userId: {}, subscriptionId: {}, scheduleId: {}",
                            targetUserId, subscription.getId(), scheduleId);
                } catch (Exception e) {
                    log.error("회원 탈퇴 결제 스케줄 취소 실패 - userId: {}, subscriptionId: {}, scheduleId: {}",
                            targetUserId, subscription.getId(), scheduleId, e);
                }
                subscription.setCurrentPaymentScheduleId(null);
            }
            subscription.cancel();
            subscriptionRepository.saveAndFlush(subscription);
        });

        List<PaymentMethod> activeMethods = paymentMethodRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(targetUserId);
        for (PaymentMethod method : activeMethods) {
            method.unsetDefault();
            method.deactivate();
        }

        List<Session> sessions = sessionRepository.findByUserIdOrderByLastAccessedAtDesc(targetUserId);
        for (Session session : sessions) {
            RefreshToken refreshToken = session.getRefreshToken();
            if (refreshToken != null) {
                session.updateRefreshToken(null);
                refreshTokenRepository.delete(refreshToken);
            }
        }
        user.getSessions().clear();
        sessionRepository.deleteAll(sessions);
        sessionRepository.flush();

        if (accessToken != null && !accessToken.isBlank()) {
            long remainingExpiration = jwtService.getRemainingExpiration(accessToken);
            if (remainingExpiration > 0) {
                tokenBlacklistService.save(accessToken, remainingExpiration);
            }
        }

        user.withdraw(Instant.now());
        log.info("회원 탈퇴 처리 완료 - userId: {}", targetUserId);
    }

    private User findActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));
    }
}
