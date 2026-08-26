package app.mockly.domain.interview.event;

import java.util.UUID;

public record FeedbackRequestedEvent(UUID sessionId) {
}
