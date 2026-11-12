package app.mockly.domain.user.service;

import app.mockly.domain.auth.dto.UserInfo;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.JwtService;
import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.payment.client.PortOneService;
import app.mockly.domain.user.dto.request.SaveUserAgreementsRequest;
import app.mockly.domain.user.dto.request.UpdateUserProfileRequest;
import app.mockly.domain.user.entity.AgreementType;
import app.mockly.domain.user.entity.UserAgreement;
import app.mockly.domain.user.repository.UserAgreementRepository;
import app.mockly.domain.user.service.UserAccountStateService.WithdrawalPreparation;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
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
    private final UserAccountStateService userAccountStateService;
    private final PortOneService portOneService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public void saveUserAgreements(UUID userId, SaveUserAgreementsRequest request) {
        User user = findActiveUser(userId);

        Map<AgreementType, Boolean> submitted = new EnumMap<>(AgreementType.class);
        for (SaveUserAgreementsRequest.AgreementItem item : request.agreements()) {
            AgreementType type = AgreementType.fromCode(item.type());
            if (submitted.put(type, item.agreed()) != null) {
                throw new BusinessException(
                        ApiStatusCode.VALIDATION_ERROR,
                        "중복된 약관 항목입니다: " + type.getCode()
                );
            }
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

    public void withdrawUser(UUID authenticatedUserId, UUID targetUserId, String accessToken) {
        WithdrawalPreparation preparation = userAccountStateService.prepareWithdrawal(
                authenticatedUserId,
                targetUserId
        );

        String revokedScheduleId = null;
        if (preparation.scheduleIdToRevoke() != null && !preparation.scheduleIdToRevoke().isBlank()) {
            try {
                portOneService.revokePaymentSchedule(preparation.scheduleIdToRevoke());
                revokedScheduleId = preparation.scheduleIdToRevoke();
                log.info("회원 탈퇴 결제 스케줄 취소 완료 - userId: {}, subscriptionId: {}, scheduleId: {}",
                        targetUserId, preparation.subscriptionId(), revokedScheduleId);
            } catch (Exception e) {
                log.error("회원 탈퇴 결제 스케줄 취소 실패 - userId: {}, subscriptionId: {}, scheduleId: {}",
                        targetUserId, preparation.subscriptionId(), preparation.scheduleIdToRevoke(), e);
                throw new BusinessException(
                        ApiStatusCode.EXTERNAL_PAYMENT_ERROR,
                        "외부 결제 예약 취소에 실패하여 회원 탈퇴를 완료할 수 없습니다. 잠시 후 다시 시도해주세요.",
                        e
                );
            }
        }

        userAccountStateService.completeWithdrawal(targetUserId, revokedScheduleId);

        if (accessToken != null && !accessToken.isBlank()) {
            try {
                long remainingExpiration = jwtService.getRemainingExpiration(accessToken);
                if (remainingExpiration > 0) {
                    tokenBlacklistService.save(accessToken, remainingExpiration);
                }
            } catch (Exception e) {
                log.warn("회원 탈퇴 액세스 토큰 블랙리스트 등록 실패 - userId: {}", targetUserId, e);
            }
        }

        log.info("회원 탈퇴 처리 완료 - userId: {}", targetUserId);
    }

    private User findActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));
    }
}
