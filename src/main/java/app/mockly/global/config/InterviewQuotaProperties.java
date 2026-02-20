package app.mockly.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "interview.quota")
public record InterviewQuotaProperties(
        int free,
        int basic,
        int pro
) {
}
