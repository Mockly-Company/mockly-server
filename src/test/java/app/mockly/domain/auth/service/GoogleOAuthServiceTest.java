package app.mockly.domain.auth.service;

import app.mockly.domain.auth.dto.GoogleUser;
import app.mockly.global.exception.InvalidTokenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleOAuthService 테스트")
class GoogleOAuthServiceTest {
    @Mock
    private GoogleIdTokenVerifier verifier;

    @InjectMocks
    private GoogleOAuthService googleOAuthService;

    @Test
    @DisplayName("유효한 Google ID token으로 GoogleUser 반환")
    void verifyIdToken_WithValidToken_ReturnGoogleUser() throws Exception {
        String validIdToken = "valid-id-token";

        GoogleIdToken mockGoogleIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = new GoogleIdToken.Payload();

        mockPayload.setSubject("unique-subject-id");
        mockPayload.setEmail("mockly@example.com");
        mockPayload.setEmailVerified(true);
        mockPayload.set("name", "mockly-user");

        given(verifier.verify(validIdToken)).willReturn(mockGoogleIdToken);
        given(mockGoogleIdToken.getPayload()).willReturn(mockPayload);

        GoogleUser result = googleOAuthService.verifyIdToken(validIdToken);

        assertThat(result).isNotNull();
        assertThat(result.sub()).isEqualTo("unique-subject-id");
        assertThat(result.email()).isEqualTo("mockly@example.com");
        assertThat(result.emailVerified()).isTrue();
        assertThat(result.name()).isEqualTo("mockly-user");
    }

    @Test
    @DisplayName("검증 시 Null을 반환시키는 ID Token은 InvalidTokenException 발생")
    void verifyIdToken_WithInvalidToken_ThrowsInvalidTokenException() throws Exception {
        String invalidToken = "invalid-id-token";
        given(verifier.verify(invalidToken)).willReturn(null);

        assertThatThrownBy(() -> googleOAuthService.verifyIdToken(invalidToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("유효하지 않은");
    }
}