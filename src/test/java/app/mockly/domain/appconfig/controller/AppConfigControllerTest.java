package app.mockly.domain.appconfig.controller;

import app.mockly.domain.appconfig.controller.docs.AppConfigDocs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app-policy.platforms.android.minimum-supported-build=100",
        "app-policy.platforms.android.latest-build=110",
        "app-policy.platforms.android.latest-version=1.7.0",
        "app-policy.platforms.android.store-url=https://play.example/mockly",
        "app-policy.platforms.ios.minimum-supported-build=80",
        "app-policy.platforms.ios.latest-build=85",
        "app-policy.platforms.ios.latest-version=1.6.0",
        "app-policy.platforms.ios.store-url=https://apps.example/mockly",
        "app-policy.maintenance.active=true",
        "app-policy.maintenance.message=서비스 안정화를 위한 점검입니다.",
        "app-policy.maintenance.starts-at=2026-09-09T01:00:00+09:00",
        "app-policy.maintenance.ends-at=2026-09-09T02:00:00+09:00"
})
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class AppConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/app-status - 플랫폼별 업데이트 및 점검 정보를 조회한다")
    void getAppConfig() throws Exception {
        mockMvc.perform(get("/api/app-status")
                        .header("X-Client-Platform", "IOS")
                        .header("X-Client-Build", "82")
                        .header("X-Client-Version", "1.5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.update.status").value("RECOMMENDED"))
                .andExpect(jsonPath("$.data.update.latestVersion").value("1.6.0"))
                .andExpect(jsonPath("$.data.update.storeUrl").value("https://apps.example/mockly"))
                .andExpect(jsonPath("$.data.maintenance.active").value(true))
                .andExpect(jsonPath("$.data.maintenance.message").value("서비스 안정화를 위한 점검입니다."))
                .andExpect(jsonPath("$.data.maintenance.startsAt").value("2026-09-09T01:00:00+09:00"))
                .andExpect(jsonPath("$.data.maintenance.endsAt").value("2026-09-09T02:00:00+09:00"))
                .andDo(document("app-status",
                        resource(AppConfigDocs.success())
                ));
    }

    @Test
    @DisplayName("GET /api/app-status - 지원하지 않는 플랫폼은 거부한다")
    void rejectsUnsupportedPlatform() throws Exception {
        mockMvc.perform(get("/api/app-status")
                        .header("X-Client-Platform", "WEB")
                        .header("X-Client-Build", "82")
                        .header("X-Client-Version", "1.5.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("INVALID_CLIENT_INFO"))
                .andDo(document("app-status-invalid-client-info",
                        resource(AppConfigDocs.invalidClientInfo())
                ));
    }

    @Test
    @DisplayName("GET /api/app-status - 필수 클라이언트 헤더가 없으면 거부한다")
    void rejectsMissingClientHeader() throws Exception {
        mockMvc.perform(get("/api/app-status")
                        .header("X-Client-Platform", "IOS")
                        .header("X-Client-Version", "1.5.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CLIENT_INFO"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "0", "-1", "9223372036854775808"})
    @DisplayName("GET /api/app-status - 유효하지 않은 build는 거부한다")
    void rejectsInvalidBuild(String build) throws Exception {
        mockMvc.perform(get("/api/app-status")
                        .header("X-Client-Platform", "ANDROID")
                        .header("X-Client-Build", build)
                        .header("X-Client-Version", "1.5.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CLIENT_INFO"));
    }
}
