package app.mockly.domain.interview.dto;

import app.mockly.domain.interview.entity.InterviewType;

public record ImprovementPracticeEvaluationContext(
        int questionNumber,
        String questionText,
        String originalAnswer,
        String improvementTitle,
        String improvementSummary,
        String submittedAnswer,
        InterviewType interviewType
) {
}
