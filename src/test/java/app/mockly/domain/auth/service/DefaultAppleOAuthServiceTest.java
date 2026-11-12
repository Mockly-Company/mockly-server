package app.mockly.domain.auth.service;

import app.mockly.domain.auth.dto.AppleUser;
import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.config.AppleOAuthProperties;
import app.mockly.global.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DefaultAppleOAuthServiceTest {

    @Test
    @DisplayName("ES256 client_secret 생성 및 Apple 토큰 교환·RS256 ID Token 검증이 정상 동작한다")
    void exchangeAndVerifyAppleIdToken_Success() throws Exception {
        KeyPairGenerator ecGen = KeyPairGenerator.getInstance("EC");
        ecGen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ecKeyPair = ecGen.generateKeyPair();
        String ecPrivatePem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(ecKeyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";

        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(2048);
        KeyPair rsaKeyPair = rsaGen.generateKeyPair();

        AppleOAuthProperties props = new AppleOAuthProperties();
        props.setClientId("com.mockly.mobile");
        props.setTeamId("TEAM123456");
        props.setKeyId("KEY1234567");
        props.setPrivateKey(ecPrivatePem);
        props.setTokenUri("https://appleid.apple.com/auth/token");
        props.setIssuer("https://appleid.apple.com");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) rsaKeyPair.getPublic()).build();
        decoder.setJwtValidator(DefaultAppleOAuthService.buildTokenValidator(props));

        DefaultAppleOAuthService service = new DefaultAppleOAuthService(builder.build(), props, decoder);

        String clientSecret = service.generateClientSecret();
        Claims secretClaims = Jwts.parser()
                .verifyWith(ecKeyPair.getPublic())
                .build()
                .parseSignedClaims(clientSecret)
                .getPayload();
        assertThat(secretClaims.getIssuer()).isEqualTo("TEAM123456");
        assertThat(secretClaims.getSubject()).isEqualTo("com.mockly.mobile");

        Instant now = Instant.now();
        String signedIdToken = Jwts.builder()
                .issuer("https://appleid.apple.com")
                .subject("apple-user-sub-777")
                .audience().add("com.mockly.mobile").and()
                .claim("email", "user777@icloud.com")
                .claim("email_verified", "true")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(5, ChronoUnit.MINUTES)))
                .signWith(rsaKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        mockServer.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"at\",\"expires_in\":3600,\"id_token\":\"" + signedIdToken + "\",\"token_type\":\"Bearer\"}",
                        MediaType.APPLICATION_JSON
                ));

        String exchangedIdToken = service.exchangeAuthorizationCode("valid-code", "verifier", "mockly://oauth/callback");
        AppleUser verifiedUser = service.verifyIdToken(exchangedIdToken);

        mockServer.verify();
        assertThat(verifiedUser.sub()).isEqualTo("apple-user-sub-777");
        assertThat(verifiedUser.email()).isEqualTo("user777@icloud.com");
        assertThat(verifiedUser.emailVerified()).isTrue();
    }

    @Test
    @DisplayName("Audience 불일치 또는 4xx 응답 시 INVALID_APPLE_TOKEN 예외를 던진다")
    void invalidAudienceOr4xxThrowsInvalidAppleToken() throws Exception {
        KeyPairGenerator ecGen = KeyPairGenerator.getInstance("EC");
        ecGen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ecKeyPair = ecGen.generateKeyPair();

        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(2048);
        KeyPair rsaKeyPair = rsaGen.generateKeyPair();

        AppleOAuthProperties props = new AppleOAuthProperties();
        props.setClientId("com.mockly.mobile");
        props.setTeamId("TEAM123456");
        props.setKeyId("KEY1234567");
        props.setPrivateKey(Base64.getEncoder().encodeToString(ecKeyPair.getPrivate().getEncoded()));
        props.setTokenUri("https://appleid.apple.com/auth/token");
        props.setIssuer("https://appleid.apple.com");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        mockServer.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andRespond(withBadRequest().body("{\"error\":\"invalid_grant\"}").contentType(MediaType.APPLICATION_JSON));

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) rsaKeyPair.getPublic()).build();
        decoder.setJwtValidator(DefaultAppleOAuthService.buildTokenValidator(props));
        DefaultAppleOAuthService service = new DefaultAppleOAuthService(builder.build(), props, decoder);

        assertThatThrownBy(() -> service.exchangeAuthorizationCode("bad-code", "verifier", "mockly://oauth/callback"))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("statusCode")
                .isEqualTo(ApiStatusCode.INVALID_APPLE_TOKEN);

        Instant now = Instant.now();
        String wrongAudienceToken = Jwts.builder()
                .issuer("https://appleid.apple.com")
                .subject("apple-user-sub-777")
                .audience().add("com.other.app").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(5, ChronoUnit.MINUTES)))
                .signWith(rsaKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> service.verifyIdToken(wrongAudienceToken))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("statusCode")
                .isEqualTo(ApiStatusCode.INVALID_APPLE_TOKEN);
    }
}
