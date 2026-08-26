package app.mockly.domain.interview;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.product.entity.PlanTier;

import java.util.List;

public final class FeedbackTestFixtures {
    private FeedbackTestFixtures() {}

    public static InterviewFeedbackResult feedbackResult(PlanTier tier) {
        boolean free = tier == PlanTier.FREE;
        return new InterviewFeedbackResult(
                80,
                new InterviewFeedbackResult.CoachBrief(
                        "핵심 요약",
                        free ? null : "핵심 강점",
                        free ? null : "핵심 개선점"),
                new InterviewFeedbackResult.Scores(78, 85, 81, 76),
                free ? List.of() : List.of(
                        new InterviewFeedbackResult.Strength(1, "강점 1", "강점 상세 1", "강점 인용 1", 1),
                        new InterviewFeedbackResult.Strength(2, "강점 2", "강점 상세 2", "강점 인용 2", 2),
                        new InterviewFeedbackResult.Strength(3, "강점 3", "강점 상세 3", "강점 인용 3", 3)),
                free ? List.of(
                        new InterviewFeedbackResult.Improvement(1, 1, "개선점 1", "개선 요약 1", null, null)) : List.of(
                        new InterviewFeedbackResult.Improvement(1, 1, "개선점 1", "개선 요약 1", "개선 상세 1", "개선 인용 1"),
                        new InterviewFeedbackResult.Improvement(2, 2, "개선점 2", "개선 요약 2", "개선 상세 2", "개선 인용 2"),
                        new InterviewFeedbackResult.Improvement(3, 3, "개선점 3", "개선 요약 3", "개선 상세 3", "개선 인용 3")),
                tier == PlanTier.PRO ? "결론부터 답변하기" : null
        );
    }
}
