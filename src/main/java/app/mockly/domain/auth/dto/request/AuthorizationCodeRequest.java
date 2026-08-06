package app.mockly.domain.auth.dto.request;

import app.mockly.domain.auth.dto.DeviceInfo;
import app.mockly.domain.auth.dto.LocationInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AuthorizationCodeRequest(
        @NotBlank(message = "Authorization code는 필수입니다.")
        String code,
        @NotBlank(message = "Code Verifier는 필수입니다.")
        String codeVerifier,
        @NotBlank(message = "Redirect URI는 필수입니다.")
        String redirectUri,
        @Valid
        @NotNull(message = "deviceInfo는 필수입니다.")
        DeviceInfo deviceInfo,
        LocationInfo locationInfo // Optional
) {
}
