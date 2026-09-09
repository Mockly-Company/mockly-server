package app.mockly.domain.appconfig.service;

import app.mockly.domain.appconfig.config.AppConfigProperties;
import app.mockly.domain.appconfig.dto.AppPlatform;
import app.mockly.domain.appconfig.dto.UpdateStatus;
import app.mockly.domain.appconfig.dto.response.GetAppConfigResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigServiceTest {

    @Test
    @DisplayName("요청 플랫폼에 해당하는 버전 정책으로 업데이트 상태를 계산한다")
    void resolvesVersionPolicyByPlatform() {
        AppConfigProperties properties = properties(Map.of(
                AppPlatform.ANDROID, platformPolicy(100, 110, "1.7.0", "https://play.example/mockly"),
                AppPlatform.IOS, platformPolicy(80, 85, "1.6.0", "https://apps.example/mockly")));
        AppConfigService service = new AppConfigService(properties, new AppVersionPolicy());

        GetAppConfigResponse androidResponse = service.getAppConfig(AppPlatform.ANDROID, 99);
        GetAppConfigResponse iosResponse = service.getAppConfig(AppPlatform.IOS, 82);

        assertThat(androidResponse.update().status()).isEqualTo(UpdateStatus.REQUIRED);
        assertThat(androidResponse.update().latestVersion()).isEqualTo("1.7.0");
        assertThat(androidResponse.update().storeUrl()).isEqualTo("https://play.example/mockly");
        assertThat(iosResponse.update().status()).isEqualTo(UpdateStatus.RECOMMENDED);
        assertThat(iosResponse.update().latestVersion()).isEqualTo("1.6.0");
        assertThat(iosResponse.update().storeUrl()).isEqualTo("https://apps.example/mockly");
    }

    @Test
    @DisplayName("설정된 점검 상태와 안내 정보를 반환한다")
    void returnsMaintenanceInformation() {
        AppConfigProperties properties = properties(Map.of(
                AppPlatform.ANDROID, platformPolicy(100, 110, "1.7.0", "https://play.example/mockly")));
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-09-09T01:00:00+09:00");
        OffsetDateTime endsAt = OffsetDateTime.parse("2026-09-09T02:00:00+09:00");
        properties.getMaintenance().setActive(true);
        properties.getMaintenance().setMessage("서비스 안정화를 위한 점검입니다.");
        properties.getMaintenance().setStartsAt(startsAt);
        properties.getMaintenance().setEndsAt(endsAt);
        AppConfigService service = new AppConfigService(properties, new AppVersionPolicy());

        GetAppConfigResponse response = service.getAppConfig(AppPlatform.ANDROID, 110);

        assertThat(response.maintenance().active()).isTrue();
        assertThat(response.maintenance().message()).isEqualTo("서비스 안정화를 위한 점검입니다.");
        assertThat(response.maintenance().startsAt()).isEqualTo(startsAt);
        assertThat(response.maintenance().endsAt()).isEqualTo(endsAt);
    }

    private AppConfigProperties properties(Map<AppPlatform, AppConfigProperties.PlatformPolicy> platforms) {
        AppConfigProperties properties = new AppConfigProperties();
        properties.setPlatforms(new EnumMap<>(platforms));
        return properties;
    }

    private AppConfigProperties.PlatformPolicy platformPolicy(
            long minimumSupportedBuild,
            long latestBuild,
            String latestVersion,
            String storeUrl
    ) {
        AppConfigProperties.PlatformPolicy policy = new AppConfigProperties.PlatformPolicy();
        policy.setMinimumSupportedBuild(minimumSupportedBuild);
        policy.setLatestBuild(latestBuild);
        policy.setLatestVersion(latestVersion);
        policy.setStoreUrl(storeUrl);
        return policy;
    }
}
