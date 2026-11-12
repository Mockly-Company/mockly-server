package app.mockly.domain.auth.service;

import app.mockly.domain.auth.dto.AppleUser;
import app.mockly.domain.auth.dto.DeviceInfo;
import app.mockly.domain.auth.dto.GoogleUser;
import app.mockly.domain.auth.dto.LocationInfo;
import app.mockly.domain.auth.dto.Platform;
import app.mockly.domain.auth.dto.UserInfo;
import app.mockly.domain.auth.dto.request.DevLoginRequest;
import app.mockly.domain.auth.dto.request.LoginWithAppleCodeRequest;
import app.mockly.domain.auth.dto.response.LoginResponse;
import app.mockly.domain.auth.dto.response.RefreshTokenResponse;
import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.RefreshToken;
import app.mockly.domain.auth.entity.Session;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.RefreshTokenRepository;
import app.mockly.domain.auth.repository.SessionRepository;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.product.service.SubscriptionService;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.config.JwtProperties;
import app.mockly.global.exception.BusinessException;
import app.mockly.global.exception.InvalidTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final GoogleOAuthService googleOAuthService;
    private final AppleOAuthService appleOAuthService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final SubscriptionService subscriptionService;

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public LoginResponse loginWithDev(DevLoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(existing -> !existing.isDeleted())
                .orElseGet(() -> {
                    GoogleUser googleUser = new GoogleUser(
                            UUID.randomUUID().toString(),
                            request.email(),
                            request.name(),
                            true
                    );
                    return createUser(googleUser);
                });

        return issueSessionAndTokens(user, request.deviceInfo(), request.locationInfo());
    }

    @Transactional
    public LoginResponse loginWithGoogleCode(String code, String codeVerifier, String redirectUri,
                                             DeviceInfo deviceInfo, LocationInfo locationInfo, Platform platform) {
        String idToken = googleOAuthService.exchangeAuthorizationCode(code, codeVerifier, redirectUri, platform);
        GoogleUser googleUser = googleOAuthService.verifyIdToken(idToken);
        User user = userRepository.findByProviderAndProviderId(OAuth2Provider.GOOGLE, googleUser.sub())
                .filter(existing -> !existing.isDeleted())
                .or(() -> findExistingActiveUserByEmail(googleUser.email()))
                .orElseGet(() -> createUser(googleUser));

        return issueSessionAndTokens(user, deviceInfo, locationInfo);
    }

    @Transactional
    public LoginResponse loginWithAppleCode(LoginWithAppleCodeRequest request) {
        String idToken = appleOAuthService.exchangeAuthorizationCode(
                request.code(),
                request.codeVerifier(),
                request.redirectUri()
        );
        AppleUser appleUser = appleOAuthService.verifyIdToken(idToken);
        User user = userRepository.findByProviderAndProviderId(OAuth2Provider.APPLE, appleUser.sub())
                .filter(existing -> !existing.isDeleted())
                .or(() -> findExistingActiveUserByEmail(appleUser.email()))
                .orElseGet(() -> createAppleUser(appleUser, request.fullName()));

        return issueSessionAndTokens(user, request.deviceInfo(), request.locationInfo());
    }

    private java.util.Optional<User> findExistingActiveUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return java.util.Optional.empty();
        }
        return userRepository.findByEmail(email)
                .filter(existing -> !existing.isDeleted());
    }

    private LoginResponse issueSessionAndTokens(User user, DeviceInfo deviceInfo, LocationInfo locationInfo) {
        Session session = sessionRepository.findByUserAndDeviceId(user, deviceInfo.deviceId())
                .orElseGet(() -> createSession(user, deviceInfo, locationInfo));

        String refreshTokenValue = jwtService.generateRefreshToken();
        RefreshToken refreshToken = createRefreshToken(refreshTokenValue);
        session.updateRefreshToken(refreshToken);
        session.updateAccessInfo(locationInfo);

        removeOldestSessions(user);

        String accessToken = jwtService.generateAccessToken(user.getId());
        return new LoginResponse(
                accessToken,
                refreshTokenValue,
                jwtProperties.getAccessTokenExpiration(),
                UserInfo.from(user)
        );
    }

    private User createUser(GoogleUser googleUser) {
        User newUser = User.from(googleUser);
        User savedUser = userRepository.save(newUser);

        subscriptionService.assignFreePlan(savedUser.getId());

        return savedUser;
    }

    private User createAppleUser(AppleUser appleUser, String fullName) {
        User newUser = User.fromApple(appleUser, fullName);
        User savedUser = userRepository.save(newUser);

        subscriptionService.assignFreePlan(savedUser.getId());

        return savedUser;
    }

    private Session createSession(User user, DeviceInfo deviceInfo, LocationInfo locationInfo) {
        return sessionRepository.save(Session.create(user, deviceInfo, locationInfo));
    }

    private RefreshToken createRefreshToken(String token) {
        Instant expiresAt = Instant.now()
                .plusMillis(jwtProperties.getRefreshTokenExpiration());

        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .expiresAt(expiresAt)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    private void removeOldestSessions(User user) {
        List<Session> activeSessions = sessionRepository.findActiveSessionsByUser(user, Instant.now());

        if (activeSessions.size() > 2) {
            int deleteCount = activeSessions.size() - 2;
            List<Session> sessionsToDelete = activeSessions.subList(0, deleteCount);
            for (Session session : sessionsToDelete) {
                session.updateRefreshToken(null);
            }
        }
    }

    @Transactional
    public RefreshTokenResponse refreshToken(String refreshTokenValue, String deviceId, LocationInfo locationInfo) {
        RefreshToken oldRefreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new InvalidTokenException(ApiStatusCode.INVALID_TOKEN, "유효하지 않은 Refresh Token입니다."));

        if (oldRefreshToken.isExpired()) {
            Session session = oldRefreshToken.getSession();
            if (session != null) {
                session.updateRefreshToken(null);
            }
            throw new InvalidTokenException(ApiStatusCode.EXPIRED_TOKEN, "만료된 토큰입니다.");
        }

        Session session = oldRefreshToken.getSession();
        if (session == null) {
            throw new InvalidTokenException(ApiStatusCode.INVALID_TOKEN, "유효하지 않은 Refresh Token입니다.");
        }
        User user = session.getUser();
        if (user.isDeleted()) {
            throw new InvalidTokenException(ApiStatusCode.INVALID_TOKEN, "탈퇴한 사용자입니다.");
        }

        if (!session.getDeviceId().equals(deviceId)) {
            throw new InvalidTokenException(ApiStatusCode.INVALID_TOKEN, "다른 디바이스에서의 토큰 갱신은 허용되지 않습니다.");
        }

        String newRefreshTokenValue = jwtService.generateRefreshToken();
        RefreshToken newRefreshToken = createRefreshToken(newRefreshTokenValue);
        session.updateRefreshToken(newRefreshToken);
        session.updateAccessInfo(locationInfo);

        String newAccessToken = jwtService.generateAccessToken(user.getId());
        return new RefreshTokenResponse(
                newAccessToken,
                newRefreshTokenValue,
                jwtProperties.getAccessTokenExpiration()
        );
    }

    @Transactional(readOnly = true)
    public UserInfo getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new BusinessException(ApiStatusCode.USER_NOT_FOUND));
        return UserInfo.from(user);
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null) {
            long remainingExpiration = jwtService.getRemainingExpiration(accessToken);
            if (remainingExpiration > 0) {
                tokenBlacklistService.save(accessToken, remainingExpiration);
            }
        }

        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(rt -> {
                    Session session = rt.getSession();
                    if (session != null) {
                        session.updateRefreshToken(null);
                    }
                });
    }
}
