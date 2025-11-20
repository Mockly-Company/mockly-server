package app.mockly.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AuthorizationCodeRequest(
        @NotBlank(message = "Authorization code는 필수입니다.")
        String code,
        @NotBlank(message = "Code Verifier는 필수입니다.")
        String codeVerifier,
        @NotBlank(message = "Redirect URI는 필수입니다.")
        String redirectUri
) {
}
