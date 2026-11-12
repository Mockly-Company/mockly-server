package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.entity.ImprovementPractice;
import app.mockly.domain.interview.entity.ImprovementPracticeStatus;

public record CreateImprovementPracticeResponse(
        Long id,
        ImprovementPracticeStatus status
) {
    public static CreateImprovementPracticeResponse from(ImprovementPractice practice) {
        return new CreateImprovementPracticeResponse(practice.getId(), practice.getStatus());
    }
}
