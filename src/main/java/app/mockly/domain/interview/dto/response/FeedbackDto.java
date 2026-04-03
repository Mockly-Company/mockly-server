package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.InterviewFeedback;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public record FeedbackDto(
        int overallScore,
        List<InterviewFeedbackResult.ExpertFeedback> expertFeedbacks,
        String strengths,
        String improvements,
        String detailedAnalysis
) {
    public static FeedbackDto from(InterviewFeedback feedback, ObjectMapper objectMapper) {
        try {
            List<InterviewFeedbackResult.ExpertFeedback> expertFeedbacks = objectMapper.readValue(
                    feedback.getExpertFeedbacks(),
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, InterviewFeedbackResult.ExpertFeedback.class));
            return new FeedbackDto(
                    feedback.getOverallScore(),
                    expertFeedbacks,
                    feedback.getStrengths(),
                    feedback.getImprovements(),
                    feedback.getDetailedAnalysis());
        } catch (Exception e) {
            log.error("피드백 역직렬화 실패 feedbackId={}", feedback.getId(), e);
            return new FeedbackDto(feedback.getOverallScore(), List.of(),
                    feedback.getStrengths(), feedback.getImprovements(), feedback.getDetailedAnalysis());
        }
    }
}
