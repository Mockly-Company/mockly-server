package app.mockly.domain.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("preprod")
@Transactional
class DevLoginPreprodIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("pre-prod 프로필에서는 dev 로그인 엔드포인트를 제공하지 않는다")
    void devLoginIsNotAvailableInPreprod() throws Exception {
        mockMvc.perform(post("/api/auth/dev/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "preprod-test@example.com",
                                  "name": "Preprod Test",
                                  "deviceInfo": {
                                    "deviceId": "preprod-device",
                                    "deviceName": "Preprod Device"
                                  },
                                  "locationInfo": {
                                    "latitude": 37.0,
                                    "longitude": 127.0
                                  }
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }
}
