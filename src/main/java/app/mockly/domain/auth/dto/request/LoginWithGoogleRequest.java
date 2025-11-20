package app.mockly.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginWithGoogleRequest(
    @NotBlank(message = "ID Token은 필수입니다.")
    String idToken
) {
}
