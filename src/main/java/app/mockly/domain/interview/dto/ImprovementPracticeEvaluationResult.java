package app.mockly.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ImprovementPracticeEvaluationResult(
        @JsonProperty(required = true, value = "score") int score,
        @JsonProperty(required = true, value = "summary") String summary,
        @JsonProperty(required = true, value = "detail") String detail,
        @JsonProperty(required = true, value = "quote") String quote
) {
}
