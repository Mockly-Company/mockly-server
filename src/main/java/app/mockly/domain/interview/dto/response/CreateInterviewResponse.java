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
        String status,
        String greeting
) {
    public static CreateInterviewResponse from(InterviewSession session, String greeting) {
        return new CreateInterviewResponse(
                session.getId(),
                session.getPosition(),
                session.getExperienceLevel(),
                session.getInterviewType(),
                session.getTotalQuestions(),
                session.getStatus().name(),
                greeting
        );
    }
}
