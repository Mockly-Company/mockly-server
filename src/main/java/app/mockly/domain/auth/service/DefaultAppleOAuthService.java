package app.mockly.domain.auth.service;

import app.mockly.domain.auth.dto.AppleToken;
import app.mockly.domain.auth.dto.AppleUser;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.config.AppleOAuthProperties;
import app.mockly.global.exception.InvalidTokenException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class DefaultAppleOAuthService implements AppleOAuthService {
    private static final long CLIENT_SECRET_VALIDITY_MINUTES = 10;

    private final RestClient restClient;
    private final AppleOAuthProperties properties;
    private volatile JwtDecoder jwtDecoder;

    @Autowired
    public DefaultAppleOAuthService(RestClient restClient, AppleOAuthProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    DefaultAppleOAuthService(RestClient restClient, AppleOAuthProperties properties, JwtDecoder jwtDecoder) {
        this.restClient = restClient;
        this.properties = properties;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public String exchangeAuthorizationCode(String code, String codeVerifier, String redirectUri) {
        if (!properties.isClientSecretConfigured()) {
            throw new IllegalStateException("oauth.apple (team-id, key-id, private-key) 설정이 완료되지 않았습니다");
        }

        String clientSecret = generateClientSecret();
        String formBody = "client_id=" + encode(properties.getClientId())
                + "&client_secret=" + encode(clientSecret)
                + "&code=" + encode(code)
                + "&grant_type=authorization_code"
                + "&redirect_uri=" + encode(redirectUri)
                + "&code_verifier=" + encode(codeVerifier);

        AppleToken appleToken = restClient.post()
                .uri(properties.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    log.error("Apple Token 교환 실패: status={} redirectUri={} response={}",
                            res.getStatusCode(), redirectUri, readErrorBody(res));
                    throw new InvalidTokenException(ApiStatusCode.INVALID_APPLE_TOKEN, "Apple 인증 코드가 유효하지 않습니다");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("Apple Server Error: {}", res.getStatusCode());
                    throw new RuntimeException("Apple 서버 오류가 발생했습니다");
                })
                .body(AppleToken.class);

        if (appleToken == null || appleToken.idToken() == null || appleToken.idToken().isBlank()) {
            throw new InvalidTokenException(ApiStatusCode.INVALID_APPLE_TOKEN, "Apple Token 교환에 실패했습니다");
        }
        return appleToken.idToken();
    }

    @Override
    public AppleUser verifyIdToken(String idToken) {
        try {
            Jwt jwt = getOrCreateJwtDecoder().decode(idToken);
            String sub = jwt.getSubject();
            if (sub == null || sub.isBlank()) {
                throw new InvalidTokenException(ApiStatusCode.INVALID_APPLE_TOKEN, "유효하지 않은 Apple ID Token입니다");
            }
            String email = jwt.getClaimAsString("email");
            Object emailVerifiedClaim = jwt.getClaims().get("email_verified");
            boolean emailVerified = Boolean.TRUE.equals(emailVerifiedClaim)
                    || "true".equalsIgnoreCase(String.valueOf(emailVerifiedClaim));
            return new AppleUser(sub, email, emailVerified);
        } catch (JwtException e) {
            log.warn("Apple ID Token 검증 실패: {}", e.getMessage());
            throw new InvalidTokenException(ApiStatusCode.INVALID_APPLE_TOKEN, "유효하지 않은 Apple ID Token입니다");
        }
    }

    private JwtDecoder getOrCreateJwtDecoder() {
        JwtDecoder localDecoder = this.jwtDecoder;
        if (localDecoder == null) {
            synchronized (this) {
                localDecoder = this.jwtDecoder;
                if (localDecoder == null) {
                    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwksUri()).build();
                    decoder.setJwtValidator(buildTokenValidator(properties));
                    this.jwtDecoder = decoder;
                    localDecoder = decoder;
                }
            }
        }
        return localDecoder;
    }

    static OAuth2TokenValidator<Jwt> buildTokenValidator(AppleOAuthProperties properties) {
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(properties.getClientId())
        );
        return new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator);
    }

    String generateClientSecret() {
        try {
            PrivateKey privateKey = parseEcPrivateKey(properties.getPrivateKey());
            Instant now = Instant.now();
            return Jwts.builder()
                    .header()
                    .keyId(properties.getKeyId())
                    .and()
                    .issuer(properties.getTeamId())
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plus(CLIENT_SECRET_VALIDITY_MINUTES, ChronoUnit.MINUTES)))
                    .audience().add(properties.getIssuer()).and()
                    .subject(properties.getClientId())
                    .signWith(privateKey, Jwts.SIG.ES256)
                    .compact();
        } catch (Exception e) {
            log.error("Apple client_secret 생성 실패");
            throw new IllegalStateException("Apple client_secret 생성에 실패했습니다", e);
        }
    }

    private PrivateKey parseEcPrivateKey(String rawPem) throws Exception {
        String normalized = rawPem
                .replace("\\n", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("EC").generatePrivate(keySpec);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }

    private String readErrorBody(ClientHttpResponse response) {
        try {
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(본문 읽기 실패: " + e.getMessage() + ")";
        }
    }
}
