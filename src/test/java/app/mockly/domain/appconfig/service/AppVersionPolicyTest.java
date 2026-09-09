package app.mockly.domain.appconfig.service;

import app.mockly.domain.appconfig.dto.UpdateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppVersionPolicyTest {

    private final AppVersionPolicy appVersionPolicy = new AppVersionPolicy();

    @Test
    @DisplayName("클라이언트 build가 최소 지원 build보다 낮으면 필수 업데이트다")
    void requiresUpdateBelowMinimumSupportedBuild() {
        UpdateStatus status = appVersionPolicy.determine(99, 100, 110);

        assertThat(status).isEqualTo(UpdateStatus.REQUIRED);
    }

    @Test
    @DisplayName("지원되지만 최신보다 낮은 build면 업데이트를 권장한다")
    void recommendsUpdateBelowLatestBuild() {
        UpdateStatus status = appVersionPolicy.determine(100, 100, 110);

        assertThat(status).isEqualTo(UpdateStatus.RECOMMENDED);
    }

    @Test
    @DisplayName("최신 build 이상이면 업데이트가 필요하지 않다")
    void doesNotUpdateAtOrAboveLatestBuild() {
        assertThat(appVersionPolicy.determine(110, 100, 110)).isEqualTo(UpdateStatus.NONE);
        assertThat(appVersionPolicy.determine(111, 100, 110)).isEqualTo(UpdateStatus.NONE);
    }
}
