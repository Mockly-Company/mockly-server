package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.entity.FeedbackStatus;

public record FeedbackStatusInfo(FeedbackStatus feedbackStatus, String failReason) {}
