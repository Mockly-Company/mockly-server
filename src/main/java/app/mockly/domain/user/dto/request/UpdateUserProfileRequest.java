package app.mockly.domain.user.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 1, max = 50, message = "닉네임은 1자 이상 50자 이하여야 합니다.")
        String name,
        @Size(max = 30, message = "희망 직군은 30자 이하여야 합니다.")
        String jobCategory,
        @Size(max = 30, message = "희망 직무는 30자 이하여야 합니다.")
        String jobRole,
        @Min(value = 0, message = "경력 연차는 0년 이상이어야 합니다.")
        @Max(value = 40, message = "경력 연차는 40년 이하여야 합니다.")
        Integer experienceYears
) {
}
