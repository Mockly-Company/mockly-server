package app.mockly.domain.interview.dto.response;

import app.mockly.domain.interview.dto.SessionSummaryDto;
import app.mockly.domain.interview.dto.WeeklyQuotaContext;
import app.mockly.domain.interview.entity.InterviewSession;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record GetInterviewOverviewResponse(
        Summary summary,
        Score score,
        List<SessionSummaryDto> recentInterviews,
        String nextPracticePoint
) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static GetInterviewOverviewResponse of(
            WeeklyQuotaContext context,
            long completedCount,
            long totalPracticeSeconds,
            List<Integer> latestScores,
            List<InterviewSession> recentInterviews,
            String nextPracticePoint
    ) {
        Summary summary = new Summary(
                context.periodStart().atStartOfDay(KST).toOffsetDateTime(),
                context.nextResetAt().atStartOfDay(KST).toOffsetDateTime(),
                completedCount,
                totalPracticeSeconds
        );

        return new GetInterviewOverviewResponse(
                summary,
                Score.from(latestScores),
                recentInterviews.stream().map(SessionSummaryDto::from).toList(),
                nextPracticePoint
        );
    }

    public record Summary(
            OffsetDateTime periodStart,
            OffsetDateTime nextResetAt,
            long completedCount,
            long totalPracticeSeconds
    ) {
    }

    public record Score(Integer latest, Integer change) {

        private static Score from(List<Integer> latestScores) {
            if (latestScores.isEmpty()) {
                return new Score(null, null);
            }

            Integer latest = latestScores.getFirst();
            if (latestScores.size() == 1) {
                return new Score(latest, null);
            }

            return new Score(latest, latest - latestScores.get(1));
        }
    }
}
