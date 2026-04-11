package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.InterviewSession;

import java.util.UUID;

public record SubmitAnswerResponse(
        UUID sessionId,
        int currentQuestionNumber,
        int totalQuestions,
        boolean isCompleted,
        String closingMessage,
        InterviewFeedbackResult feedback
) {
    public static SubmitAnswerResponse inProgress(InterviewSession session) {
        return new SubmitAnswerResponse(
                session.getId(),
                session.getCurrentQuestionNumber(),
                session.getTotalQuestions(),
                false,
                null,
                null
        );
    }

    public static SubmitAnswerResponse completed(InterviewSession session, InterviewFeedbackResult feedback) {
        return new SubmitAnswerResponse(
                session.getId(),
                session.getCurrentQuestionNumber(),
                session.getTotalQuestions(),
                true,
                "면접이 종료되었습니다. 오늘 면접에 응해주셔서 감사합니다. 수고하셨습니다.",
                feedback
        );
    }
}
