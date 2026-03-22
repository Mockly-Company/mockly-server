package app.mockly.domain.interview.dto.request;

import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInterviewRequest(
        @NotBlank(message = "포지션은 필수입니다.")
        @Size(max = 100, message = "포지션은 100자 이내로 입력해주세요.")
        String position,

        @NotNull(message = "경력 수준은 필수입니다.")
        ExperienceLevel experienceLevel,

        @NotNull(message = "면접 유형은 필수입니다.")
        InterviewType interviewType,

        @NotNull(message = "질문 개수는 필수입니다.")
        Integer totalQuestions
) {
}
