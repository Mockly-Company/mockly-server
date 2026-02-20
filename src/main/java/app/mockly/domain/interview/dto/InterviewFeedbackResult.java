package app.mockly.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record InterviewFeedbackResult(
        @JsonPropertyDescription("면접 종합 점수 (1~100)")
        int overallScore,
        @JsonPropertyDescription("강점 분석 (마크다운)")
        String strengths,
        @JsonPropertyDescription("개선점 분석 (마크다운)")
        String improvements,
        @JsonPropertyDescription("질문별 상세 분석 (마크다운)")
        String detailedAnalysis
) {
}
