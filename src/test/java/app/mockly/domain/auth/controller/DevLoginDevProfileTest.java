package app.mockly.domain.auth.controller;

import app.mockly.global.logging.PreprodRequestAccessLogFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DevLoginDevProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("dev 프로필에서는 pre-prod 요청 접근 로그 필터를 등록하지 않는다")
    void preprodRequestAccessLogFilterIsNotRegisteredInDev() {
        assertThat(applicationContext.getBeansOfType(PreprodRequestAccessLogFilter.class)).isEmpty();
    }

    @Test
    @DisplayName("dev 프로필에서는 dev 로그인 엔드포인트를 제공한다")
    void devLoginIsAvailableInDev() throws Exception {
        mockMvc.perform(post("/api/auth/dev/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dev-test@example.com",
                                  "name": "Dev Test",
                                  "deviceInfo": {
                                    "deviceId": "dev-device",
                                    "deviceName": "Dev Device"
                                  },
                                  "locationInfo": {
                                    "latitude": 37.0,
                                    "longitude": 127.0
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.email").value("dev-test@example.com"));
    }
}
