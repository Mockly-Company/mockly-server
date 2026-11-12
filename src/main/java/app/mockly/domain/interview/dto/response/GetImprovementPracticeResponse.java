package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.entity.ImprovementPractice;
import app.mockly.domain.interview.entity.ImprovementPracticeStatus;

public record GetImprovementPracticeResponse(
        Long id,
        ImprovementPracticeStatus status,
        int questionNumber,
        String questionText,
        String originalAnswer,
        String submittedAnswer,
        ResultDto result
) {
    public record ResultDto(
            Integer score,
            String summary,
            String detail,
            String quote
    ) {
    }

    public static GetImprovementPracticeResponse from(ImprovementPractice practice) {
        ResultDto resultDto = practice.getStatus() == ImprovementPracticeStatus.COMPLETED
                ? new ResultDto(
                        practice.getScore(),
                        practice.getSummary(),
                        practice.getDetail(),
                        practice.getQuote()
                )
                : null;
        return new GetImprovementPracticeResponse(
                practice.getId(),
                practice.getStatus(),
                practice.getQuestionNumber(),
                practice.getQuestionText(),
                practice.getOriginalAnswer(),
                practice.getSubmittedAnswer(),
                resultDto
        );
    }
}
