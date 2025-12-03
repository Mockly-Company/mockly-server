package app.mockly.domain.auth.dto.request;

import app.mockly.domain.auth.dto.LocationInfo;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh Token은 필수입니다.")
        String refreshToken,
        LocationInfo locationInfo // optional
) {
}
