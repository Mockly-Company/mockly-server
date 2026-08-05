package app.mockly.global.config;

import app.mockly.domain.auth.dto.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2PropertiesTest {

    private static final String ANDROID_CLIENT_ID = "android-client-id.apps.googleusercontent.com";
    private static final String IOS_CLIENT_ID = "ios-client-id.apps.googleusercontent.com";

    private OAuth2Properties properties(Map<Platform, String> clientIds) {
        OAuth2Properties properties = new OAuth2Properties();
        properties.setClientIds(new EnumMap<>(clientIds));
        return properties;
    }

    @Test
    @DisplayName("플랫폼별로 서로 다른 client id를 반환한다")
    void resolvesClientIdPerPlatform() {
        OAuth2Properties properties = properties(Map.of(
                Platform.ANDROID, ANDROID_CLIENT_ID,
                Platform.IOS, IOS_CLIENT_ID));

        assertThat(properties.getClientId(Platform.ANDROID)).isEqualTo(ANDROID_CLIENT_ID);
        assertThat(properties.getClientId(Platform.IOS)).isEqualTo(IOS_CLIENT_ID);
    }

    @Test
    @DisplayName("audience는 설정된 모든 플랫폼의 client id를 포함한다")
    void audiencesContainEveryConfiguredClientId() {
        OAuth2Properties properties = properties(Map.of(
                Platform.ANDROID, ANDROID_CLIENT_ID,
                Platform.IOS, IOS_CLIENT_ID));

        assertThat(properties.getAudiences())
                .containsExactlyInAnyOrder(ANDROID_CLIENT_ID, IOS_CLIENT_ID);
    }

    @Test
    @DisplayName("iOS client id 미설정 시 Android는 정상 동작하고 iOS 로그인만 실패한다")
    void missingIosClientIdDoesNotBreakAndroid() {
        OAuth2Properties properties = properties(Map.of(
                Platform.ANDROID, ANDROID_CLIENT_ID,
                Platform.IOS, ""));

        assertThat(properties.getClientId(Platform.ANDROID)).isEqualTo(ANDROID_CLIENT_ID);
        assertThat(properties.getAudiences()).containsExactly(ANDROID_CLIENT_ID);
        assertThatThrownBy(() -> properties.getClientId(Platform.IOS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-ids.ios");
    }
}
