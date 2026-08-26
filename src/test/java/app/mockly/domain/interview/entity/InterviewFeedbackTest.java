package app.mockly.domain.interview.entity;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.product.entity.PlanTier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewFeedbackTest {

    @Test
    void createMapsStructuredResultToFeedbackAndChildren() {
        InterviewSession session = InterviewSession.builder().build();
        InterviewFeedbackResult result = new InterviewFeedbackResult(
                82,
                new InterviewFeedbackResult.CoachBrief("요약", "강점", "개선점"),
                new InterviewFeedbackResult.Scores(78, 85, 81, 76),
                List.of(new InterviewFeedbackResult.Strength(2, "구체적인 경험", "상세", "인용", 1)),
                List.of(new InterviewFeedbackResult.Improvement(1, 1, "결론부터 답변", "한 줄 요약", "상세", "인용")),
                "결론을 먼저 말하기"
        );

        InterviewFeedback feedback = InterviewFeedback.create(session, result, PlanTier.PRO);

        assertThat(feedback.getOverallScore()).isEqualTo(82);
        assertThat(feedback.getCoachBriefSummary()).isEqualTo("요약");
        assertThat(feedback.getScoreStructure()).isEqualTo(78);
        assertThat(feedback.getGeneratedTier()).isEqualTo(PlanTier.PRO);
        assertThat(feedback.getStrengths()).singleElement()
                .extracting(FeedbackStrength::getTitle)
                .isEqualTo("구체적인 경험");
        assertThat(feedback.getImprovements()).singleElement()
                .extracting(FeedbackImprovement::getRank)
                .isEqualTo(1);
    }
}
