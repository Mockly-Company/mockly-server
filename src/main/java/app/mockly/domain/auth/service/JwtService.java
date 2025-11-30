package app.mockly.domain.auth.service;

import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.config.JwtProperties;
import app.mockly.global.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtProperties jwtProperties;

    // TODO: 추후 Membership 추가 시, Role 추가
    public String generateAccessToken(UUID userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration());

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public UUID validateAccessToken(String token) {
        try {
            Claims claims = extractClaims(token);
            String userId = claims.getSubject();
            return UUID.fromString(userId);
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException(ApiStatusCode.EXPIRED_TOKEN, "만료된 토큰입니다.");
        } catch (JwtException e) {
            throw new InvalidTokenException(ApiStatusCode.INVALID_TOKEN, "유효하지 않은 토큰입니다");
        }
    }

    public long getRemainingExpiration(String token) {
        Claims claims = extractClaims(token);
        Date expiration = claims.getExpiration();
        long expirationMs = expiration.getTime();
        long nowMs = System.currentTimeMillis();
        return expirationMs - nowMs;
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
