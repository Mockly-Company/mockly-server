package app.mockly.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "oauth.apple")
public class AppleOAuthProperties {
    private String clientId = "com.mockly.mobile";
    private String teamId;
    private String keyId;
    private String privateKey;
    private String tokenUri = "https://appleid.apple.com/auth/token";
    private String jwksUri = "https://appleid.apple.com/auth/keys";
    private String issuer = "https://appleid.apple.com";

    public boolean isClientSecretConfigured() {
        return teamId != null && !teamId.isBlank()
                && keyId != null && !keyId.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }
}
