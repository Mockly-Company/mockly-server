package app.mockly.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceInfo(
        @NotBlank(message = "deviceId는 필수입니다.")
        String deviceId,
        @NotBlank(message = "deviceName은 필수입니다.")
        String deviceName
) {
}
