package app.mockly.domain.plan.service;

import app.mockly.domain.plan.dto.PlanInfo;
import app.mockly.domain.plan.dto.response.PlansResponse;
import app.mockly.domain.plan.entity.Plan;
import app.mockly.domain.plan.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanService {
    private final PlanRepository planRepository;

    @Transactional(readOnly = true)
    public PlansResponse getPlans(UUID userId) {
        List<Plan> activePlans = planRepository.findAllActiveWithPrices();

        // TODO: 사용자 구독 여부 확인
        boolean isActive = false;
        List<PlanInfo> planInfos = activePlans.stream()
                .map(plan -> PlanInfo.from(plan, isActive))
                .toList();

        return PlansResponse.of(planInfos);
    }
}
