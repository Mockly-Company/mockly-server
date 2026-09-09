package app.mockly.domain.appconfig.dto.request;

import app.mockly.domain.appconfig.dto.AppPlatform;
import app.mockly.global.exception.InvalidClientInfoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetAppConfigRequestTest {

    @Test
    @DisplayName("클라이언트 헤더를 앱 설정 요청으로 변환한다")
    void parsesClientHeaders() {
        GetAppConfigRequest request = GetAppConfigRequest.fromHeaders("ios", "82", "1.5.0");

        assertThat(request.platform()).isEqualTo(AppPlatform.IOS);
        assertThat(request.build()).isEqualTo(82);
        assertThat(request.version()).isEqualTo("1.5.0");
    }

    @Test
    @DisplayName("필수 클라이언트 헤더가 없거나 유효하지 않으면 요청을 거부한다")
    void rejectsInvalidClientHeaders() {
        assertThatThrownBy(() -> GetAppConfigRequest.fromHeaders(null, "82", "1.5.0"))
                .isInstanceOf(InvalidClientInfoException.class);
        assertThatThrownBy(() -> GetAppConfigRequest.fromHeaders("WEB", "82", "1.5.0"))
                .isInstanceOf(InvalidClientInfoException.class);
        assertThatThrownBy(() -> GetAppConfigRequest.fromHeaders("IOS", "not-a-number", "1.5.0"))
                .isInstanceOf(InvalidClientInfoException.class);
        assertThatThrownBy(() -> GetAppConfigRequest.fromHeaders("IOS", "0", "1.5.0"))
                .isInstanceOf(InvalidClientInfoException.class);
        assertThatThrownBy(() -> GetAppConfigRequest.fromHeaders("IOS", "82", " "))
                .isInstanceOf(InvalidClientInfoException.class);
    }
}
