package app.mockly.domain.user.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveUserAgreementsRequest(
        @NotEmpty(message = "약관 동의 목록은 필수입니다.")
        @Valid
        List<AgreementItem> agreements
) {
    public record AgreementItem(
            @NotBlank(message = "약관 유형은 필수입니다.")
            String type,
            @NotNull(message = "동의 여부는 필수입니다.")
            Boolean agreed
    ) {
    }
}
