package app.mockly.domain.auth.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformTest {

    // 실제 pre-prod traefik 접근 로그에서 관측된 값
    private static final String IOS_USER_AGENT = "mockly/1 CFNetwork/3860.100.1 Darwin/25.5.0";
    private static final String ANDROID_USER_AGENT = "okhttp/4.12.0";

    @Test
    @DisplayName("요청 본문의 platform이 User-Agent보다 우선한다")
    void requestedPlatformWinsOverUserAgent() {
        assertThat(Platform.resolve(Platform.ANDROID, IOS_USER_AGENT)).isEqualTo(Platform.ANDROID);
        assertThat(Platform.resolve(Platform.IOS, ANDROID_USER_AGENT)).isEqualTo(Platform.IOS);
    }

    @Test
    @DisplayName("platform 미전송 시 애플 네이티브 User-Agent는 IOS로 판별한다")
    void appleUserAgentResolvesToIos() {
        assertThat(Platform.resolve(null, IOS_USER_AGENT)).isEqualTo(Platform.IOS);
        assertThat(Platform.resolve(null, "Mockly/2.0 (com.mockly.app; iOS 18.0) Alamofire/5.9.1")).isEqualTo(Platform.IOS);
    }

    @Test
    @DisplayName("애플 시그니처가 없거나 User-Agent가 없으면 ANDROID로 처리한다")
    void otherUserAgentsFallBackToAndroid() {
        assertThat(Platform.resolve(null, ANDROID_USER_AGENT)).isEqualTo(Platform.ANDROID);
        assertThat(Platform.resolve(null, null)).isEqualTo(Platform.ANDROID);
        assertThat(Platform.resolve(null, "")).isEqualTo(Platform.ANDROID);
    }

    @Test
    @DisplayName("HTTP 클라이언트 이름에 포함된 ios는 애플로 오판하지 않는다")
    void httpClientNamesAreNotMistakenForIos() {
        assertThat(Platform.resolve(null, "axios/1.6.0")).isEqualTo(Platform.ANDROID);
        assertThat(Platform.resolve(null, "Dart/3.4 (dart:io)")).isEqualTo(Platform.ANDROID);
    }

    @Test
    @DisplayName("판별 근거를 로그로 구분할 수 있다")
    void exposesResolveSource() {
        assertThat(Platform.resolveSource(Platform.IOS)).isEqualTo("request");
        assertThat(Platform.resolveSource(null)).isEqualTo("user-agent");
    }
}
