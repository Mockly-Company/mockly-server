package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.dto.WeeklyQuotaContext;
import app.mockly.domain.product.entity.SubscriptionProduct;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GetInterviewOverviewResponseTest {

    private final WeeklyQuotaContext context = WeeklyQuotaContext.of(
            SubscriptionProduct.builder().build(),
            LocalDate.of(2026, 8, 24),
            true
    );

    @Test
    void createsKstPeriodAndScoreChange() {
        GetInterviewOverviewResponse response = GetInterviewOverviewResponse.of(
                context,
                3,
                2_400,
                List.of(82, 78),
                List.of(),
                null
        );

        assertThat(response.summary().periodStart().toString()).isEqualTo("2026-08-24T00:00+09:00");
        assertThat(response.summary().nextResetAt().toString()).isEqualTo("2026-08-31T00:00+09:00");
        assertThat(response.summary().completedCount()).isEqualTo(3L);
        assertThat(response.summary().totalPracticeSeconds()).isEqualTo(2_400);
        assertThat(response.score().latest()).isEqualTo(82);
        assertThat(response.score().change()).isEqualTo(4);
    }

    @Test
    void distinguishesNoScoreFromNoPreviousScore() {
        GetInterviewOverviewResponse noScore = GetInterviewOverviewResponse.of(
                context, 0, 0, List.of(), List.of(), null);
        GetInterviewOverviewResponse oneScore = GetInterviewOverviewResponse.of(
                context, 0, 0, List.of(82), List.of(), null);

        assertThat(noScore.score().latest()).isNull();
        assertThat(noScore.score().change()).isNull();
        assertThat(oneScore.score().latest()).isEqualTo(82);
        assertThat(oneScore.score().change()).isNull();
    }

    @Test
    void preservesZeroAndNegativeScoreChanges() {
        GetInterviewOverviewResponse unchanged = GetInterviewOverviewResponse.of(
                context, 0, 0, List.of(75, 75), List.of(), null);
        GetInterviewOverviewResponse decreased = GetInterviewOverviewResponse.of(
                context, 0, 0, List.of(70, 75), List.of(), null);

        assertThat(unchanged.score().change()).isZero();
        assertThat(decreased.score().change()).isEqualTo(-5);
    }
}
