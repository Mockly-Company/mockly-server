package app.mockly.domain.auth.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformTest {

    // 실제 traefik 접근 로그에서 관측된 iOS 앱의 User-Agent
    private static final String IOS_USER_AGENT = "mockly/1 CFNetwork/3860.100.1 Darwin/25.5.0";

    @Test
    @DisplayName("애플 네이티브 User-Agent는 IOS로 판별한다")
    void appleUserAgentResolvesToIos() {
        assertThat(Platform.fromUserAgent(IOS_USER_AGENT)).isEqualTo(Platform.IOS);
        // Alamofire는 UA를 덮어써서 CFNetwork/Darwin이 남지 않는다
        assertThat(Platform.fromUserAgent("Mockly/2.0 (com.mockly.app; iOS 18.0) Alamofire/5.9.1"))
                .isEqualTo(Platform.IOS);
        assertThat(Platform.fromUserAgent("Mockly/1.0 (iPhone; iOS 17.5)")).isEqualTo(Platform.IOS);
    }

    @Test
    @DisplayName("애플 시그니처가 없거나 User-Agent가 없으면 ANDROID로 처리한다")
    void otherUserAgentsFallBackToAndroid() {
        assertThat(Platform.fromUserAgent("okhttp/4.12.0")).isEqualTo(Platform.ANDROID);
        assertThat(Platform.fromUserAgent(null)).isEqualTo(Platform.ANDROID);
        assertThat(Platform.fromUserAgent("")).isEqualTo(Platform.ANDROID);
    }

    @Test
    @DisplayName("HTTP 클라이언트 이름에 포함된 ios는 애플로 오판하지 않는다")
    void httpClientNamesAreNotMistakenForIos() {
        assertThat(Platform.fromUserAgent("axios/1.6.0")).isEqualTo(Platform.ANDROID);
        assertThat(Platform.fromUserAgent("Dart/3.4 (dart:io)")).isEqualTo(Platform.ANDROID);
    }
}
