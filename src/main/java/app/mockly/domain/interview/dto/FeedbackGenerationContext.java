package app.mockly.domain.interview.dto;

import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.product.entity.PlanTier;

import java.util.List;

public record FeedbackGenerationContext(
        List<InterviewMessage> history,
        InterviewType interviewType,
        PlanTier planTier
) {
}
