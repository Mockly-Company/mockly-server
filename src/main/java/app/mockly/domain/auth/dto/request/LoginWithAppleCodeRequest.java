package app.mockly.domain.auth.dto.request;

import app.mockly.domain.auth.dto.DeviceInfo;
import app.mockly.domain.auth.dto.LocationInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginWithAppleCodeRequest(
        @NotBlank(message = "Authorization code는 필수입니다.")
        String code,
        @NotBlank(message = "Code Verifier는 필수입니다.")
        String codeVerifier,
        @NotBlank(message = "Redirect URI는 필수입니다.")
        String redirectUri,
        @Valid
        @NotNull(message = "deviceInfo는 필수입니다.")
        DeviceInfo deviceInfo,
        LocationInfo locationInfo,
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String fullName
) {
}
