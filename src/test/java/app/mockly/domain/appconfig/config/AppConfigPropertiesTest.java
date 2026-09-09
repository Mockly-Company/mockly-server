package app.mockly.domain.appconfig.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "app-policy.platforms.android.minimum-supported-build=100",
                    "app-policy.platforms.android.latest-build=110",
                    "app-policy.platforms.android.latest-version=1.7.0",
                    "app-policy.platforms.android.store-url=https://play.example/mockly",
                    "app-policy.platforms.ios.minimum-supported-build=80",
                    "app-policy.platforms.ios.latest-build=85",
                    "app-policy.platforms.ios.latest-version=1.6.0",
                    "app-policy.platforms.ios.store-url=https://apps.example/mockly",
                    "app-policy.maintenance.active=false");

    @Test
    @DisplayName("최소 지원 build가 최신 build보다 크면 애플리케이션 시작을 거부한다")
    void rejectsInvalidBuildRange() {
        contextRunner
                .withPropertyValues("app-policy.platforms.android.latest-build=90")
                .run(context -> {
            assertThat(context).hasFailed();
        });
    }

    @Test
    @DisplayName("점검 활성 시 안내 메시지가 없으면 애플리케이션 시작을 거부한다")
    void rejectsActiveMaintenanceWithoutMessage() {
        contextRunner
                .withPropertyValues("app-policy.maintenance.active=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("점검 시작 시각이 종료 시각보다 늦으면 애플리케이션 시작을 거부한다")
    void rejectsInvalidMaintenanceTimeRange() {
        contextRunner
                .withPropertyValues(
                        "app-policy.maintenance.starts-at=2026-09-09T03:00:00+09:00",
                        "app-policy.maintenance.ends-at=2026-09-09T02:00:00+09:00")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppConfigProperties.class)
    static class TestConfiguration {
    }
}
