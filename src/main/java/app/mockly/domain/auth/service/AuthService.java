package app.mockly.domain.auth.service;

import app.mockly.domain.auth.dto.GoogleUser;
import app.mockly.domain.auth.dto.UserInfo;
import app.mockly.domain.auth.dto.response.LoginResponse;
import app.mockly.domain.auth.dto.response.RefreshTokenResponse;
import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.RefreshToken;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.RefreshTokenRepository;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.config.JwtProperties;
import app.mockly.global.exception.InvalidTokenException;
import app.mockly.global.exception.ResourceNotFoundException;
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
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public LoginResponse loginWithGoogleCode(String code, String codeVerifier, String redirectUri) {
        String idToken = googleOAuthService.exchangeAuthorizationCode(code, codeVerifier, redirectUri);
        GoogleUser googleUser = googleOAuthService.verifyIdToken(idToken);
        User user = userRepository.findByProviderAndProviderId(OAuth2Provider.GOOGLE, googleUser.sub())
                .orElseGet(() -> createUser(googleUser));

        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshToken = jwtService.generateRefreshToken();
        saveRefreshToken(user, refreshToken);
        removeOldestRefreshToken(user);

        return new LoginResponse(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenExpiration(),
                new UserInfo(
                        user.getId().toString(),
                        user.getEmail(),
                        user.getName()
                )
        );
    }

    @Transactional
    public LoginResponse loginWithGoogle(String idToken) {
        GoogleUser googleUser = googleOAuthService.verifyIdToken(idToken);
        User user = userRepository.findByProviderAndProviderId(OAuth2Provider.GOOGLE, googleUser.sub())
                .orElseGet(() -> createUser(googleUser));

        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshToken = jwtService.generateRefreshToken();
        saveRefreshToken(user, refreshToken);
        removeOldestRefreshToken(user);

        return new LoginResponse(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenExpiration(),
                UserInfo.from(user)
        );
    }

    @Transactional
    public RefreshTokenResponse refreshToken(String refreshToken) {
        RefreshToken oldRefreshToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new InvalidTokenException(ApiStatusCode.INVALID_TOKEN, "유효하지 않은 Refresh Token입니다."));

        if (oldRefreshToken.isExpired()) {
            refreshTokenRepository.delete(oldRefreshToken);
            throw new InvalidTokenException(ApiStatusCode.EXPIRED_TOKEN, "만료된 토큰입니다.");
        }

        User user = oldRefreshToken.getUser();
        String newAccessToken = jwtService.generateAccessToken(user.getId());
        String newRefreshToken = jwtService.generateRefreshToken();
        saveRefreshToken(user, newRefreshToken);
        return new RefreshTokenResponse(
                newAccessToken,
                newRefreshToken,
                jwtProperties.getAccessTokenExpiration()
        );
    }

    private User createUser(GoogleUser googleUser) {
        User newUser = User.from(googleUser);
        return userRepository.save(newUser);
    }

    private RefreshToken saveRefreshToken(User user, String token) {
        Instant expiresAt = Instant.now()
                .plusMillis(jwtProperties.getRefreshTokenExpiration());

        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiresAt(expiresAt)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    private void removeOldestRefreshToken(User user) {
        List<RefreshToken> validTokens = refreshTokenRepository.findValidTokensByUser(user, Instant.now());

        if (validTokens.size() > 2) {
            int deleteCount = validTokens.size() - 2;
            List<RefreshToken> tokensToDelete = validTokens.subList(0, deleteCount);
            refreshTokenRepository.deleteAll(tokensToDelete);
        }
    }

    @Transactional(readOnly = true)
    public UserInfo getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiStatusCode.USER_NOT_FOUND));
        return UserInfo.from(user);
    }

    @Transactional
    public void logout(UUID userId, String accessToken, String refreshToken) {
        long remainingExpiration = jwtService.getRemainingExpiration(accessToken);
        tokenBlacklistService.save(accessToken, remainingExpiration);

        refreshTokenRepository.deleteByToken(refreshToken);
    }
}
