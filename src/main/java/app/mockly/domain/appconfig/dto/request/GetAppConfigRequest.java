package app.mockly.domain.appconfig.dto.request;

import app.mockly.domain.appconfig.dto.AppPlatform;
import app.mockly.global.exception.InvalidClientInfoException;

import java.util.Locale;

public record GetAppConfigRequest(
        AppPlatform platform,
        long build,
    String version
) {
    public static GetAppConfigRequest fromHeaders(String platform, String build, String version) {
        if (platform == null || platform.isBlank()
                || build == null || build.isBlank()
                || version == null || version.isBlank()) {
            throw new InvalidClientInfoException();
        }

        try {
            AppPlatform appPlatform = AppPlatform.valueOf(platform.trim().toUpperCase(Locale.ROOT));
            long appBuild = Long.parseLong(build.trim());
            if (appBuild <= 0) {
                throw new InvalidClientInfoException();
            }
            return new GetAppConfigRequest(appPlatform, appBuild, version.trim());
        } catch (IllegalArgumentException exception) {
            throw new InvalidClientInfoException();
        }
    }
}
