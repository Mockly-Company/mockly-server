package app.mockly.domain.plan.controller;

import app.mockly.domain.plan.dto.response.PlansResponse;
import app.mockly.domain.plan.service.PlanService;
import app.mockly.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {
    private final PlanService planService;

    @GetMapping
    public ResponseEntity<ApiResponse<PlansResponse>> getPlans(@AuthenticationPrincipal UUID userId) {
        PlansResponse plansResponse = planService.getPlans(userId);
        return ResponseEntity.ok(ApiResponse.success(plansResponse));
    }
}
