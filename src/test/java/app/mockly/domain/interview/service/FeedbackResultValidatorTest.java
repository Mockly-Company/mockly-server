package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.product.entity.PlanTier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static app.mockly.domain.interview.FeedbackTestFixtures.feedbackResult;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackResultValidatorTest {
    private final FeedbackResultValidator validator = new FeedbackResultValidator();

    @Test
    void acceptsTheContractForEveryTier() {
        assertThatCode(() -> validator.validate(feedbackResult(PlanTier.FREE), PlanTier.FREE, 3))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(feedbackResult(PlanTier.BASIC), PlanTier.BASIC, 5))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(feedbackResult(PlanTier.PRO), PlanTier.PRO, 7))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAFreeResultThatContainsPaidDetails() {
        InterviewFeedbackResult paidResult = feedbackResult(PlanTier.BASIC);

        assertThatThrownBy(() -> validator.validate(paidResult, PlanTier.FREE, 3))
                .isInstanceOf(InvalidFeedbackResultException.class);
    }

    @Test
    void rejectsEmptyStringsForFieldsThatMustNotBeGenerated() {
        InterviewFeedbackResult valid = feedbackResult(PlanTier.FREE);
        InterviewFeedbackResult invalid = new InterviewFeedbackResult(
                valid.overallScore(),
                new InterviewFeedbackResult.CoachBrief(valid.coachBrief().summary(), "", " "),
                valid.scores(),
                valid.strengths(),
                List.of(new InterviewFeedbackResult.Improvement(
                        1, 1, "개선점", "요약", "", " ")),
                "");

        assertThatThrownBy(() -> validator.validate(invalid, PlanTier.FREE, 3))
                .isInstanceOf(InvalidFeedbackResultException.class);
    }

    @Test
    void rejectsAResultWithAnOutOfRangeScore() {
        InterviewFeedbackResult valid = feedbackResult(PlanTier.BASIC);
        InterviewFeedbackResult invalid = new InterviewFeedbackResult(
                valid.overallScore(), valid.coachBrief(),
                new InterviewFeedbackResult.Scores(101, 85, 81, 76),
                valid.strengths(), valid.improvements(), valid.nextPracticePoint());

        assertThatThrownBy(() -> validator.validate(invalid, PlanTier.BASIC, 5))
                .isInstanceOf(InvalidFeedbackResultException.class);
    }

    @Test
    void rejectsAQuestionNumberOutsideTheInterview() {
        InterviewFeedbackResult valid = feedbackResult(PlanTier.BASIC);
        List<InterviewFeedbackResult.Improvement> invalidImprovements = List.of(
                new InterviewFeedbackResult.Improvement(1, 6, "개선점 1", "요약 1", "상세 1", "인용 1"),
                valid.improvements().get(1),
                valid.improvements().get(2));
        InterviewFeedbackResult invalid = new InterviewFeedbackResult(
                valid.overallScore(), valid.coachBrief(), valid.scores(),
                valid.strengths(), invalidImprovements, valid.nextPracticePoint());

        assertThatThrownBy(() -> validator.validate(invalid, PlanTier.BASIC, 5))
                .isInstanceOf(InvalidFeedbackResultException.class);
    }
}
