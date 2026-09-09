package app.mockly.domain.appconfig.service;

import app.mockly.domain.appconfig.dto.UpdateStatus;
import org.springframework.stereotype.Component;

@Component
public class AppVersionPolicy {

    public UpdateStatus determine(long clientBuild, long minimumSupportedBuild, long latestBuild) {
        if (clientBuild < minimumSupportedBuild) {
            return UpdateStatus.REQUIRED;
        }
        if (clientBuild < latestBuild) {
            return UpdateStatus.RECOMMENDED;
        }
        return UpdateStatus.NONE;
    }
}
