package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.entity.InterviewSession;

import java.util.UUID;

public record SubmitAnswerResponse(
        UUID sessionId,
        int currentQuestionNumber,
        int totalQuestions,
        String sessionStatus
) {
    public static SubmitAnswerResponse inProgress(InterviewSession session) {
        return new SubmitAnswerResponse(
                session.getId(),
                session.getCurrentQuestionNumber(),
                session.getTotalQuestions(),
                session.getStatus().name()
        );
    }

    public static SubmitAnswerResponse feedbackPending(InterviewSession session) {
        return new SubmitAnswerResponse(
                session.getId(),
                session.getCurrentQuestionNumber(),
                session.getTotalQuestions(),
                session.getStatus().name()
        );
    }
}
