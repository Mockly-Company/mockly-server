package app.mockly.domain.auth.service;

import app.mockly.domain.auth.dto.AppleUser;

public interface AppleOAuthService {
    String exchangeAuthorizationCode(String code, String codeVerifier, String redirectUri);

    AppleUser verifyIdToken(String idToken);
}
