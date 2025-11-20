package app.mockly.domain.auth.service;

import app.mockly.domain.auth.dto.GoogleToken;
import app.mockly.domain.auth.dto.GoogleUser;
import app.mockly.global.config.OAuth2Properties;
import app.mockly.global.exception.InvalidTokenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {
    private final RestClient restClient;
    private final OAuth2Properties oAuth2Properties;
    private final GoogleIdTokenVerifier verifier;

    public String exchangeAuthorizationCode(String code, String codeVerifier, String redirectUri) {
        GoogleToken googleToken = restClient.post()
                .uri(oAuth2Properties.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("code=" + code +
                        "&client_id=" + oAuth2Properties.getClientId() +
                        "&client_secret=" + oAuth2Properties.getClientSecret() +
                        "&redirect_uri=" + redirectUri +
                        "&grant_type=authorization_code" +
                        "&code_verifier=" + codeVerifier)
                .retrieve()
                .onStatus(httpStatusCode -> httpStatusCode.is4xxClientError(), (req, res) -> {
                    log.error("Google Token 교환 실패: {}", res.getStatusCode());
                    // TODO: 각 에러별 처리
                })
                .onStatus(httpStatusCode -> httpStatusCode.is5xxServerError() , (req, res) -> {
                    log.error("Google Server Error: {}", res.getStatusCode());
                })
                .body(GoogleToken.class);
        if (googleToken == null || googleToken.idToken() == null) {
            // TODO: custom exception으로 변경
            throw new RuntimeException("Google Token 교환 실패");
        }
        return googleToken.idToken();
    }

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
