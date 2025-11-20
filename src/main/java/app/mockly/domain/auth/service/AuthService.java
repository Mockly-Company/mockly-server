package app.mockly.domain.auth.service;

import app.mockly.domain.auth.dto.GoogleUser;
import app.mockly.domain.auth.dto.response.LoginResponse;
import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.RefreshToken;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.RefreshTokenRepository;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.global.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final GoogleOAuthService googleOAuthService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

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
                new LoginResponse.UserInfo(
                        user.getId().toString(),
                        user.getEmail(),
                        user.getName()
                )
        );
    }

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
                new LoginResponse.UserInfo(
                        user.getId().toString(),
                        user.getEmail(),
                        user.getName()
                )
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
}
