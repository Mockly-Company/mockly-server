package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.entity.ExperienceLevel;
import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewSession;
import app.mockly.domain.interview.entity.InterviewSessionStatus;
import app.mockly.domain.interview.entity.InterviewType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GetSessionDetailResponse(
        UUID sessionId,
        String position,
        ExperienceLevel experienceLevel,
        InterviewType interviewType,
        int totalQuestions,
        int currentQuestionNumber,
        InterviewSessionStatus status,
        Instant createdAt,
        Instant completedAt,
        List<MessageDto> messages,
        FeedbackDto feedback
) {
    public static GetSessionDetailResponse from(InterviewSession session, List<InterviewMessage> messages, FeedbackDto feedback) {
        return new GetSessionDetailResponse(
                session.getId(),
                session.getPosition(),
                session.getExperienceLevel(),
                session.getInterviewType(),
                session.getTotalQuestions(),
                session.getCurrentQuestionNumber(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getCompletedAt(),
                messages.stream().map(MessageDto::from).toList(),
                feedback
        );
    }
}
