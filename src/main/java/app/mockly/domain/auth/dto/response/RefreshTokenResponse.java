package app.mockly.domain.auth.dto.response;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        Long expiresIn
) {
}
