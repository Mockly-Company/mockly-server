package app.mockly.domain.appconfig.service;

import app.mockly.domain.appconfig.config.AppConfigProperties;
import app.mockly.domain.appconfig.dto.AppPlatform;
import app.mockly.domain.appconfig.dto.UpdateStatus;
import app.mockly.domain.appconfig.dto.response.GetAppConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppConfigService {
    private final AppConfigProperties properties;
    private final AppVersionPolicy appVersionPolicy;

    public GetAppConfigResponse getAppConfig(AppPlatform platform, long clientBuild) {
        AppConfigProperties.PlatformPolicy policy = properties.getPlatforms().get(platform);
        UpdateStatus status = appVersionPolicy.determine(
                clientBuild,
                policy.getMinimumSupportedBuild(),
                policy.getLatestBuild());

        AppConfigProperties.Maintenance maintenance = properties.getMaintenance();
        return new GetAppConfigResponse(
                new GetAppConfigResponse.UpdateInfo(
                        status,
                        policy.getLatestVersion(),
                        policy.getStoreUrl()),
                new GetAppConfigResponse.MaintenanceInfo(
                        maintenance.isActive(),
                        maintenance.getMessage(),
                        maintenance.getStartsAt(),
                        maintenance.getEndsAt()));
    }
}
