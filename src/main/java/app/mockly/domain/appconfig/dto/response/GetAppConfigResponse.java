package app.mockly.domain.appconfig.dto.response;

import app.mockly.domain.appconfig.dto.UpdateStatus;

import java.time.OffsetDateTime;

public record GetAppConfigResponse(
        UpdateInfo update,
        MaintenanceInfo maintenance
) {
    public record UpdateInfo(
            UpdateStatus status,
            String latestVersion,
            String storeUrl
    ) {
    }

    public record MaintenanceInfo(
            boolean active,
            String message,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {
    }
}
