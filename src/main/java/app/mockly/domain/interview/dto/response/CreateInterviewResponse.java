package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewType;

import java.util.UUID;

public record CreateInterviewResponse(
        UUID sessionId,
        String position,
        ExperienceLevel experienceLevel,
        InterviewType interviewType,
        int totalQuestions,
        int currentQuestionNumber,
        String status,
        String firstQuestion
) {
    public static CreateInterviewResponse from(InterviewSession session, String firstQuestion) {
        return new CreateInterviewResponse(
                session.getId(),
                session.getPosition(),
                session.getExperienceLevel(),
                session.getInterviewType(),
                session.getTotalQuestions(),
                session.getCurrentQuestionNumber(),
                session.getStatus().name(),
                firstQuestion
        );
    }
}
