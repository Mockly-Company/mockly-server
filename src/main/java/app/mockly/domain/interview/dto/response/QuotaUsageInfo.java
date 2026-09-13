package app.mockly.domain.interview.dto.response;

public record QuotaUsageInfo(int limit, int used, int remaining) {

    public static QuotaUsageInfo of(int limit, int used, boolean available) {
        int remaining = available ? Math.max(0, limit - used) : 0;
        return new QuotaUsageInfo(limit, used, remaining);
    }
}
