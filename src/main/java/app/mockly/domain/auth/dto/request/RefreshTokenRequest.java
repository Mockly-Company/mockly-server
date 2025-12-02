package app.mockly.domain.auth.dto.request;

import app.mockly.domain.auth.dto.DeviceInfo;
import app.mockly.domain.auth.dto.LocationInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh Token은 필수입니다.")
        String refreshToken,
        @Valid
        @NotNull(message = "deviceInfo는 필수입니다.")
        DeviceInfo deviceInfo,
        LocationInfo locationInfo // optional
) {
}
