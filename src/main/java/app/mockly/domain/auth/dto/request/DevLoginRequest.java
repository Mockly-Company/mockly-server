package app.mockly.domain.auth.dto.request;

import app.mockly.domain.auth.dto.DeviceInfo;
import app.mockly.domain.auth.dto.LocationInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record DevLoginRequest(
        @NotBlank(message = "email은 필수입니다.")
        String email,
        @NotBlank(message = "name은 필수입니다.")
        String name,
        @Valid
        @NotBlank
        DeviceInfo deviceInfo,
        LocationInfo locationInfo
) {
}
