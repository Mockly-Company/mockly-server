package app.mockly.domain.auth.service;

import app.mockly.domain.auth.dto.GoogleToken;
import app.mockly.domain.auth.dto.GoogleUser;
import app.mockly.domain.auth.dto.Platform;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.config.OAuth2Properties;
import app.mockly.global.exception.InvalidTokenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {
    private final RestClient restClient;
    private final OAuth2Properties oAuth2Properties;
    private final GoogleIdTokenVerifier verifier;

    public String exchangeAuthorizationCode(String code, String codeVerifier, String redirectUri, Platform platform) {
        GoogleToken googleToken = restClient.post()
                .uri(oAuth2Properties.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("code=" + code +
                        "&client_id=" + oAuth2Properties.getClientId(platform) +
                        "&redirect_uri=" + redirectUri +
                        "&grant_type=authorization_code" +
                        "&code_verifier=" + codeVerifier)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    // Google 응답 본문에는 error/error_description만 담기므로 로그로 남겨도 안전하다.
                    // code와 code_verifier는 자격증명이므로 절대 남기지 않는다.
                    log.error("Google Token 교환 실패: status={} platform={} redirectUri={} response={}",
                            res.getStatusCode(), platform, redirectUri, readErrorBody(res));
                    throw new InvalidTokenException(ApiStatusCode.INVALID_GOOGLE_TOKEN, "Google 인증 코드가 유효하지 않습니다");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("Google Server Error: {}", res.getStatusCode());
                    // TODO: 외부 API 연동 실패 예외 처리 (ExternalApiException 등)
                    throw new RuntimeException("Google 서버 오류가 발생했습니다");
                })
                .body(GoogleToken.class);
        if (googleToken == null || googleToken.idToken() == null) {
            // TODO: 적절한 Exception으로 변경 필요
            throw new InvalidTokenException(ApiStatusCode.INVALID_TOKEN, "Google Token 교환에 실패했습니다");
        }
        return googleToken.idToken();
    }

    private String readErrorBody(ClientHttpResponse response) {
        try {
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(본문 읽기 실패: " + e.getMessage() + ")";
        }
    }

    public GoogleUser verifyIdToken(String idToken) {
        try {
            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken == null) {
                // 이 경로는 audience(client id) 불일치일 때도 타므로 로그 없이는 원인 추적이 불가능하다
                log.warn("Google ID Token 검증 실패: audience/issuer/만료 중 하나가 불일치");
                throw new InvalidTokenException(ApiStatusCode.INVALID_GOOGLE_TOKEN, "유효하지 않은 Google ID Token입니다");
            }

            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            return GoogleUser.from(payload);
        } catch (GeneralSecurityException e) {
            log.error("Google ID Token 검증 중 보안 오류 발생", e);
            // TODO: 외부 API 연동 실패 예외 처리
            throw new RuntimeException("Google ID Token 검증에 실패했습니다", e);
        } catch (IOException e) {
            log.error("Google ID Token 검증 중 IO 오류 발생", e);
            // TODO: 외부 API 연동 실패 예외 처리
            throw new RuntimeException("Google ID Token 검증에 실패했습니다", e);
        }
    }
}
