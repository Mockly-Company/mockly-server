package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.InterviewSession;

import java.util.UUID;

public record SubmitAnswerResponse(
        UUID sessionId,
        int currentQuestionNumber,
        int totalQuestions,
        boolean isCompleted,
        InterviewFeedbackResult feedback
) {
    public static SubmitAnswerResponse inProgress(InterviewSession session) {
        return new SubmitAnswerResponse(
                session.getId(),
                session.getCurrentQuestionNumber(),
                session.getTotalQuestions(),
                false,
                null
        );
    }

    public static SubmitAnswerResponse completed(InterviewSession session, InterviewFeedbackResult feedback) {
        return new SubmitAnswerResponse(
                session.getId(),
                session.getCurrentQuestionNumber(),
                session.getTotalQuestions(),
                true,
                feedback
        );
    }
}
