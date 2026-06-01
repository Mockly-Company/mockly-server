package app.mockly.domain.interview.dto.response;

public record GetQuotaResponse(
        int dailyLimit,
        int usedToday,
        int remaining,
        int maxQuestionsPerSession
) {
    public static GetQuotaResponse of(int dailyLimit, long usedToday, int maxQuestionsPerSession) {
        int used = (int) usedToday; // COUNT 결과, 일일 세션 수는 int 범위 초과 불가
        return new GetQuotaResponse(dailyLimit, used, Math.max(0, dailyLimit - used), maxQuestionsPerSession);
    }
}
