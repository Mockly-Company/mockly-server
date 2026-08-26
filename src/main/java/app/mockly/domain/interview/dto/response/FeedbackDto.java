package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.entity.FeedbackImprovement;
import app.mockly.domain.interview.entity.FeedbackStrength;
import app.mockly.domain.interview.entity.InterviewFeedback;
import app.mockly.domain.product.entity.PlanTier;

import java.util.List;

public record FeedbackDto(
        int overallScore,
        PlanTier generatedTier,
        CoachBriefDto coachBrief,
        ScoresDto scores,
        List<StrengthDto> strengths,
        List<ImprovementDto> improvements,
        String nextPracticePoint
) {
    public static FeedbackDto from(InterviewFeedback feedback, boolean exposeScores, boolean practiceAvailable) {
        return new FeedbackDto(
                feedback.getOverallScore(),
                feedback.getGeneratedTier(),
                new CoachBriefDto(
                        feedback.getCoachBriefSummary(),
                        feedback.getCoachBriefKeyStrength(),
                        feedback.getCoachBriefKeyImprovement()),
                exposeScores ? new ScoresDto(
                        feedback.getScoreStructure(),
                        feedback.getScoreSpecificity(),
                        feedback.getScoreJobRelevance(),
                        feedback.getScoreClarity()) : null,
                feedback.getStrengths().stream().map(StrengthDto::from).toList(),
                feedback.getImprovements().stream()
                        .map(improvement -> ImprovementDto.from(improvement, practiceAvailable))
                        .toList(),
                feedback.getNextPracticePoint());
    }

    public record CoachBriefDto(String summary, String keyStrength, String keyImprovement) {}
    public record ScoresDto(int structure, int specificity, int jobRelevance, int clarity) {}
    public record StrengthDto(Long id, int questionNumber, String title, String detail, String quote, int sortOrder) {
        private static StrengthDto from(FeedbackStrength strength) {
            return new StrengthDto(strength.getId(), strength.getQuestionNumber(), strength.getTitle(),
                    strength.getDetail(), strength.getQuote(), strength.getSortOrder());
        }
    }
    public record ImprovementDto(Long id, int rank, int questionNumber, String title, String summary,
                                 String detail, String quote, boolean practiceAvailable) {
        private static ImprovementDto from(FeedbackImprovement improvement, boolean practiceAvailable) {
            boolean available = practiceAvailable && improvement.getDetail() != null && !improvement.getDetail().isBlank();
            return new ImprovementDto(improvement.getId(), improvement.getRank(), improvement.getQuestionNumber(),
                    improvement.getTitle(), improvement.getSummary(), improvement.getDetail(), improvement.getQuote(), available);
        }
    }
}
