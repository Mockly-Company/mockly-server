package app.mockly.domain.interview.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateImprovementPracticeRequest(
        @NotNull(message = "개선 포인트 ID는 필수입니다.")
        @Positive(message = "개선 포인트 ID는 양수여야 합니다.")
        Long improvementId
) {
}
