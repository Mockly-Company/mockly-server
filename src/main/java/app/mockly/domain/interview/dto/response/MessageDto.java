package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewMessageRole;

import java.time.Instant;

public record MessageDto(
        String role,
        String content,
        Integer questionNumber,
        Instant createdAt
) {
    public static MessageDto from(InterviewMessage message) {
        return new MessageDto(
                message.getRole() == InterviewMessageRole.INTERVIEWER ? "INTERVIEWER" : "USER",
                message.getContent(),
                message.getQuestionNumber(),
                message.getCreatedAt()
        );
    }
}
