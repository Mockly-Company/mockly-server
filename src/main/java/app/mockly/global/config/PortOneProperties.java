package app.mockly.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "portone")
@Validated
public record PortOneProperties(
        @NotBlank String apiSecret,
        String apiBase,
        @NotBlank String storeId,
        String channelKey
) {}
