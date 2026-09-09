package app.mockly.domain.appconfig.config;

import app.mockly.domain.appconfig.dto.AppPlatform;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;

@Getter
@Setter
@Configuration
@Validated
@ConfigurationProperties(prefix = "app-policy")
public class AppConfigProperties {
    @Valid
    @NotEmpty
    private Map<AppPlatform, PlatformPolicy> platforms = new EnumMap<>(AppPlatform.class);

    @Valid
    private Maintenance maintenance = new Maintenance();

    @AssertTrue(message = "Android와 iOS 버전 정책이 모두 필요합니다")
    public boolean isEveryPlatformConfigured() {
        return platforms.keySet().containsAll(java.util.Set.of(AppPlatform.ANDROID, AppPlatform.IOS));
    }

    @Getter
    @Setter
    public static class PlatformPolicy {
        @Min(1)
        private long minimumSupportedBuild;

        @Min(1)
        private long latestBuild;

        @NotBlank
        private String latestVersion;
        private String storeUrl;

        @AssertTrue(message = "minimumSupportedBuild는 latestBuild보다 클 수 없습니다")
        public boolean isBuildRangeValid() {
            return minimumSupportedBuild <= latestBuild;
        }
    }

    @Getter
    @Setter
    public static class Maintenance {
        private boolean active;
        private String message;
        private OffsetDateTime startsAt;
        private OffsetDateTime endsAt;

        @AssertTrue(message = "점검 활성 시 안내 메시지가 필요합니다")
        public boolean isMessagePresentWhenActive() {
            return !active || (message != null && !message.isBlank());
        }

        @AssertTrue(message = "점검 시작 시각은 종료 시각보다 늦을 수 없습니다")
        public boolean isTimeRangeValid() {
            return startsAt == null || endsAt == null || !startsAt.isAfter(endsAt);
        }
    }
}
