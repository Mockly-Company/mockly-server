package app.mockly.domain.auth.dto.response;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    Long expiresIn,
    UserInfo user
) {
    public record UserInfo(
       String id,
       String email,
       String name
    ) {}
}
