package app.mockly.domain.interview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitAnswerRequest(
        @NotBlank(message = "답변 내용은 필수입니다.")
        @Size(max = 1500, message = "답변은 1500자 이내로 입력해주세요.")
        String content
) {
}
