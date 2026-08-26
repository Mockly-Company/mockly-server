package app.mockly.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.lang.Nullable;

import java.util.List;

public record InterviewFeedbackResult(
        @JsonProperty(required = true, value = "overallScore")
        @JsonPropertyDescription("면접 종합 점수 (1~100)")
        int overallScore,
        @JsonProperty(required = true, value = "coachBrief") CoachBrief coachBrief,
        @JsonProperty(required = true, value = "scores") Scores scores,
        @JsonProperty(required = true, value = "strengths") List<Strength> strengths,
        @JsonProperty(required = true, value = "improvements") List<Improvement> improvements,
        @Nullable @JsonProperty(required = true, value = "nextPracticePoint") String nextPracticePoint
) {
    public record CoachBrief(
            @JsonProperty(required = true, value = "summary") String summary,
            @Nullable @JsonProperty(required = true, value = "keyStrength") String keyStrength,
            @Nullable @JsonProperty(required = true, value = "keyImprovement") String keyImprovement
    ) {}

    public record Scores(
            @JsonProperty(required = true, value = "structure") int structure,
            @JsonProperty(required = true, value = "specificity") int specificity,
            @JsonProperty(required = true, value = "jobRelevance") int jobRelevance,
            @JsonProperty(required = true, value = "clarity") int clarity
    ) {}

    public record Strength(
            @JsonProperty(required = true, value = "questionNumber") int questionNumber,
            @JsonProperty(required = true, value = "title") String title,
            @JsonProperty(required = true, value = "detail") String detail,
            @JsonProperty(required = true, value = "quote") String quote,
            @JsonProperty(required = true, value = "sortOrder") int sortOrder
    ) {}

    public record Improvement(
            @JsonProperty(required = true, value = "rank") int rank,
            @JsonProperty(required = true, value = "questionNumber") int questionNumber,
            @JsonProperty(required = true, value = "title") String title,
            @JsonProperty(required = true, value = "summary") String summary,
            @Nullable @JsonProperty(required = true, value = "detail") String detail,
            @Nullable @JsonProperty(required = true, value = "quote") String quote
    ) {}
}
