package app.mockly.domain.interview.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import app.mockly.domain.interview.dto.WeeklyQuotaContext;
import app.mockly.domain.product.entity.PlanTier;
import app.mockly.domain.product.entity.SubscriptionProduct;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class GetQuotaResponseTest {

    @Test
    void 주간_쿼터_계산_결과를_API_응답으로_변환한다() {
        SubscriptionProduct product = SubscriptionProduct.builder()
                .name("Free")
                .planTier(PlanTier.FREE)
                .maxQuestions(3)
                .weeklyInterviewLimit(1)
                .weeklyImprovementPracticeLimit(0)
                .build();
        WeeklyQuotaContext context = WeeklyQuotaContext.of(product, LocalDate.of(2026, 8, 17), true);

        GetQuotaResponse response = GetQuotaResponse.of(context, 1, 0);

        assertThat(context.nextResetAt()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(response.periodStart()).isEqualTo(LocalDate.of(2026, 8, 17).atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
        assertThat(response.nextResetAt()).isEqualTo(LocalDate.of(2026, 8, 24).atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
        assertThat(response.maxQuestions()).isEqualTo(3);
        assertThat(response.interview()).isEqualTo(new QuotaUsageInfo(1, 1, 0));
        assertThat(response.improvementPractice()).isEqualTo(new QuotaUsageInfo(0, 0, 0));
    }
}
