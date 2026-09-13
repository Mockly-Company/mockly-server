package app.mockly.domain.interview.dto;

import app.mockly.domain.interview.entity.FeedbackStatus;

import java.util.UUID;

public record FeedbackContext(
        FeedbackGenerationContext context,
        FeedbackStatus feedbackStatus,
        UUID taskId
) {
}
