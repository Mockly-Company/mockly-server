package app.mockly.domain.auth.dto.response;

import app.mockly.domain.auth.dto.UserInfo;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    Long expiresIn,
    UserInfo user
) {
}
