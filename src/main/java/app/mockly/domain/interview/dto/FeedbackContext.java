package app.mockly.domain.interview.dto;

import app.mockly.domain.interview.entity.FeedbackStatus;

public record FeedbackContext(
        FeedbackGenerationContext context,
        FeedbackStatus feedbackStatus
) {
}
