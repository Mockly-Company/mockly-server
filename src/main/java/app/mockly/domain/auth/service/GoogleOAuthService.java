package app.mockly.domain.auth.service;

import app.mockly.domain.auth.dto.GoogleUser;
import app.mockly.global.exception.InvalidTokenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {
    private final GoogleIdTokenVerifier verifier;

    public GoogleUser verifyIdToken(String idToken) {
        try {
            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken == null) {
                throw new InvalidTokenException("유효하지 않은 Google ID Token입니다.");
            }

            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            return GoogleUser.from(payload);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
