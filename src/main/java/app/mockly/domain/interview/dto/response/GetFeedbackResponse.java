package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.entity.FeedbackStatus;

public record GetFeedbackResponse(
        FeedbackStatus feedbackStatus,
        FeedbackDto feedback,
        String message
) {
    public static GetFeedbackResponse completed(FeedbackDto feedback) {
        return new GetFeedbackResponse(FeedbackStatus.COMPLETED, feedback, null);
    }

    public static GetFeedbackResponse pending() {
        return new GetFeedbackResponse(FeedbackStatus.PENDING, null, null);
    }

    public static GetFeedbackResponse generating() {
        return new GetFeedbackResponse(FeedbackStatus.GENERATING, null, null);
    }

    public static GetFeedbackResponse failed(String message) {
        return new GetFeedbackResponse(FeedbackStatus.FAILED, null, message);
    }
}
