package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.dto.WeeklyQuotaContext;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record GetQuotaResponse(
        OffsetDateTime periodStart,
        OffsetDateTime nextResetAt,
        int maxQuestions,
        QuotaUsageInfo interview,
        QuotaUsageInfo improvementPractice
) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static GetQuotaResponse of(WeeklyQuotaContext context, int interviewUsed, int improvementUsed) {
        return new GetQuotaResponse(
                context.periodStart().atStartOfDay(KST).toOffsetDateTime(),
                context.nextResetAt().atStartOfDay(KST).toOffsetDateTime(),
                context.product().getMaxQuestions(),
                QuotaUsageInfo.of(context.product().getWeeklyInterviewLimit(), interviewUsed, context.available()),
                QuotaUsageInfo.of(context.product().getWeeklyImprovementPracticeLimit(), improvementUsed, context.available())
        );
    }
}
