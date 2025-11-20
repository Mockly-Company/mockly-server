package app.mockly.domain.auth.controller;

import app.mockly.domain.auth.dto.request.AuthorizationCodeRequest;
import app.mockly.domain.auth.dto.response.LoginResponse;
import app.mockly.domain.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import org.springframework.restdocs.payload.JsonFieldType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    void loginWithGoogleCode() throws Exception {
        AuthorizationCodeRequest request = new AuthorizationCodeRequest(
            "test-auth-code",
            "test-code-verifier",
            "https://mockly.app/oauth2/google/redirect"
        );
        LoginResponse response = new LoginResponse(
            "access-token",
            "refresh-token",
            900000L,
            new LoginResponse.UserInfo(
                "user-id",
                "user@mockly.com",
                "mockly"
            )
        );

        given(authService.loginWithGoogleCode(anyString(), anyString(), anyString()))
                .willReturn(response);

        mockMvc.perform(post("/api/auth/login/google/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andDo(document("auth-login-google-code",
                        requestFields(
                                fieldWithPath("code").description("Google Authorization Code"),
                                fieldWithPath("codeVerifier").description("PKCE code verifier"),
                                fieldWithPath("redirectUri").description("OAuth redirect URI")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("data").type(JsonFieldType.OBJECT).description("응답 데이터"),
                                fieldWithPath("data.accessToken").type(JsonFieldType.STRING).description("Mockly JWT access token (15분 유효)"),
                                fieldWithPath("data.refreshToken").type(JsonFieldType.STRING).description("Mockly refresh token (7일 유효)"),
                                fieldWithPath("data.expiresIn").type(JsonFieldType.NUMBER).description("Access token 만료 시간 (ms)"),
                                fieldWithPath("data.user").type(JsonFieldType.OBJECT).description("사용자 정보"),
                                fieldWithPath("data.user.id").type(JsonFieldType.STRING).description("사용자 ID"),
                                fieldWithPath("data.user.email").type(JsonFieldType.STRING).description("이메일"),
                                fieldWithPath("data.user.name").type(JsonFieldType.STRING).description("이름"),
                                fieldWithPath("error").type(JsonFieldType.STRING).description("에러 코드 (실패 시)").optional(),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지").optional(),
                                fieldWithPath("timestamp").type(JsonFieldType.NUMBER).description("응답 타임스탬프 (Unix timestamp, ms)")
                        )));
    }

}