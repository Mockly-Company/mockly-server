package app.mockly.domain.interview.service;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.product.entity.PlanTier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FeedbackResultValidator {

    public void validate(InterviewFeedbackResult result, PlanTier tier, int totalQuestions) {
        require(result != null, "피드백 결과가 없습니다.");
        require(tier != null, "생성 티어가 없습니다.");
        require(totalQuestions > 0, "전체 질문 수가 올바르지 않습니다.");
        validateScore(result.overallScore(), "종합 점수");
        validateCoachBrief(result.coachBrief(), tier);
        validateScores(result.scores());
        validateStrengths(result.strengths(), tier, totalQuestions);
        validateImprovements(result.improvements(), tier, totalQuestions);
        validateNextPracticePoint(result.nextPracticePoint(), tier);
    }

    private void validateCoachBrief(InterviewFeedbackResult.CoachBrief brief, PlanTier tier) {
        require(brief != null, "코치 브리핑이 없습니다.");
        requireText(brief.summary(), 200, "코치 요약");
        if (tier == PlanTier.FREE) {
            requireNotGenerated(brief.keyStrength(), "Free 핵심 강점은 생성하지 않습니다.");
            requireNotGenerated(brief.keyImprovement(), "Free 핵심 개선점은 생성하지 않습니다.");
            return;
        }
        requireText(brief.keyStrength(), 100, "핵심 강점");
        requireText(brief.keyImprovement(), 100, "핵심 개선점");
    }

    private void validateScores(InterviewFeedbackResult.Scores scores) {
        require(scores != null, "4축 점수가 없습니다.");
        validateScore(scores.structure(), "답변 구조 점수");
        validateScore(scores.specificity(), "구체성 점수");
        validateScore(scores.jobRelevance(), "직무 연관성 점수");
        validateScore(scores.clarity(), "전달 명확성 점수");
    }

    private void validateStrengths(List<InterviewFeedbackResult.Strength> strengths,
                                   PlanTier tier, int totalQuestions) {
        require(strengths != null, "강점 목록이 없습니다.");
        int expectedSize = tier == PlanTier.FREE ? 0 : 3;
        require(strengths.size() == expectedSize, "강점 항목 수가 올바르지 않습니다.");
        Set<Integer> orders = strengths.stream()
                .map(InterviewFeedbackResult.Strength::sortOrder)
                .collect(Collectors.toSet());
        require(expectedSize == 0 || orders.equals(Set.of(1, 2, 3)), "강점 표시 순서가 올바르지 않습니다.");
        for (InterviewFeedbackResult.Strength strength : strengths) {
            requireQuestionNumber(strength.questionNumber(), totalQuestions);
            requireText(strength.title(), 120, "강점 제목");
            requireText(strength.detail(), Integer.MAX_VALUE, "강점 상세");
            requireText(strength.quote(), Integer.MAX_VALUE, "강점 인용");
        }
    }

    private void validateImprovements(List<InterviewFeedbackResult.Improvement> improvements,
                                      PlanTier tier, int totalQuestions) {
        require(improvements != null, "개선점 목록이 없습니다.");
        int expectedSize = tier == PlanTier.FREE ? 1 : 3;
        require(improvements.size() == expectedSize, "개선점 항목 수가 올바르지 않습니다.");
        Set<Integer> ranks = improvements.stream()
                .map(InterviewFeedbackResult.Improvement::rank)
                .collect(Collectors.toSet());
        require(ranks.equals(expectedSize == 1 ? Set.of(1) : Set.of(1, 2, 3)), "개선점 순위가 올바르지 않습니다.");
        for (InterviewFeedbackResult.Improvement improvement : improvements) {
            requireQuestionNumber(improvement.questionNumber(), totalQuestions);
            requireText(improvement.title(), 120, "개선점 제목");
            requireText(improvement.summary(), 200, "개선점 요약");
            if (tier == PlanTier.FREE) {
                requireNotGenerated(improvement.detail(), "Free 개선점 상세는 생성하지 않습니다.");
                requireNotGenerated(improvement.quote(), "Free 개선점 인용은 생성하지 않습니다.");
            } else {
                requireText(improvement.detail(), Integer.MAX_VALUE, "개선점 상세");
                requireText(improvement.quote(), Integer.MAX_VALUE, "개선점 인용");
            }
        }
    }

    private void validateNextPracticePoint(String nextPracticePoint, PlanTier tier) {
        if (tier == PlanTier.PRO) {
            requireText(nextPracticePoint, 60, "다음 연습 포인트");
            return;
        }
        requireNotGenerated(nextPracticePoint, "Pro 외 티어는 다음 연습 포인트를 생성하지 않습니다.");
    }

    private void requireQuestionNumber(int questionNumber, int totalQuestions) {
        require(questionNumber >= 1 && questionNumber <= totalQuestions, "질문 번호가 범위를 벗어났습니다.");
    }

    private void validateScore(int score, String fieldName) {
        require(score >= 1 && score <= 100, fieldName + "가 범위를 벗어났습니다.");
    }

    private void requireText(String value, int maxLength, String fieldName) {
        require(!isBlank(value), fieldName + "이 비어 있습니다.");
        require(value.length() <= maxLength, fieldName + "이 최대 길이를 초과했습니다.");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void requireNotGenerated(String value, String message) {
        require(value == null, message);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidFeedbackResultException(message);
        }
    }
}
