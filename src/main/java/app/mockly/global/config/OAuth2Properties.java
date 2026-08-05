package app.mockly.global.config;

import app.mockly.domain.auth.dto.Platform;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "oauth.google")
public class OAuth2Properties {
    private Map<Platform, String> clientIds = new EnumMap<>(Platform.class);
    private String clientSecret;
    private String tokenUri;
    private String issuer;

    /**
     * 토큰 교환에 사용할 플랫폼별 client id.
     * 미설정 플랫폼으로 로그인을 시도하면 Google에 빈 client_id를 보내는 대신 즉시 실패시킨다.
     */
    public String getClientId(Platform platform) {
        String clientId = clientIds.get(platform);
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(
                    "oauth.google.client-ids." + platform.name().toLowerCase() + " 설정이 없습니다");
        }
        return clientId;
    }

    /**
     * ID Token 검증용 audience 목록. 설정된 모든 플랫폼의 client id를 허용하므로
     * 검증 경로에는 플랫폼 분기가 필요 없다.
     */
    public Collection<String> getAudiences() {
        List<String> audiences = clientIds.values().stream()
                .filter(clientId -> clientId != null && !clientId.isBlank())
                .toList();
        if (audiences.isEmpty()) {
            throw new IllegalStateException("oauth.google.client-ids 설정이 비어 있습니다");
        }
        return audiences;
    }
}
