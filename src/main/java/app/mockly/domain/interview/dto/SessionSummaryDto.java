package app.mockly.domain.interview.dto;

import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewSessionStatus;
import app.mockly.domain.interview.entity.InterviewType;

import java.time.Instant;
import java.util.UUID;

public record SessionSummaryDto(
        UUID sessionId,
        String position,
        ExperienceLevel experienceLevel,
        InterviewType interviewType,
        int totalQuestions,
        InterviewSessionStatus status,
        Instant createdAt
) {
    public static SessionSummaryDto from(InterviewSession session) {
        return new SessionSummaryDto(
                session.getId(),
                session.getPosition(),
                session.getExperienceLevel(),
                session.getInterviewType(),
                session.getTotalQuestions(),
                session.getStatus(),
                session.getCreatedAt()
        );
    }
}
