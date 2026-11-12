package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.entity.ImprovementPracticeStatus;

public record ImprovementPracticeStatusInfo(
        ImprovementPracticeStatus status,
        String failReason
) {
}
