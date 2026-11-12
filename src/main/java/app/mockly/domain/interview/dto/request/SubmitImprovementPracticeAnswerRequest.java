package app.mockly.domain.interview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitImprovementPracticeAnswerRequest(
        @NotBlank(message = "개선 재답변은 필수입니다.")
        @Size(max = 1500, message = "개선 재답변은 최대 1,500자까지 입력할 수 있습니다.")
        String answer
) {
}
