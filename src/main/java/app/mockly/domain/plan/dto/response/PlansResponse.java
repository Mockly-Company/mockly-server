package app.mockly.domain.plan.dto.response;

import app.mockly.domain.plan.dto.PlanInfo;

import java.util.List;

public record PlansResponse(
    List<PlanInfo> plans
) {
    public static PlansResponse of(List<PlanInfo> plans) {
        return new PlansResponse(plans);
    }
}
