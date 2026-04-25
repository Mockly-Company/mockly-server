package app.mockly.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record InterviewFeedbackResult(
        @JsonProperty(required = true, value = "overallScore")
        @JsonPropertyDescription("면접 종합 점수 (1~100)")
        int overallScore,
        @JsonProperty(required = true, value = "expertFeedbacks")
        @JsonPropertyDescription("전문가별 평가 목록")
        List<ExpertFeedback> expertFeedbacks,
        @JsonProperty(required = true, value = "strengths")
        @JsonPropertyDescription("전반적인 강점 요약")
        String strengths,
        @JsonProperty(required = true, value = "improvements")
        @JsonPropertyDescription("전반적인 개선점 요약")
        String improvements,
        @JsonProperty(required = true, value = "detailedAnalysis")
        @JsonPropertyDescription("질문별 상세 분석 (PRO 플랜 전용, 그 외 플랜은 null)")
        String detailedAnalysis
) {
    public record ExpertFeedback(
            @JsonProperty(required = true, value = "expertRole")
            @JsonPropertyDescription("전문가 역할 (예: 기술 면접관, 커뮤니케이션 전문가, 면접 코치)")
            String expertRole,
            @JsonProperty(required = true, value = "score")
            @JsonPropertyDescription("해당 전문가 관점의 평가 점수 (1~100)")
            int score,
            @JsonProperty(required = true, value = "evaluation")
            @JsonPropertyDescription("해당 전문가 관점의 평가 내용")
            String evaluation
    ) {
    }
}
