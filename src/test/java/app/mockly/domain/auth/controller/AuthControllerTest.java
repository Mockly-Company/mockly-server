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
                                fieldWithPath("accessToken").description("Mockly JWT access token (15분 유효)"),
                                fieldWithPath("refreshToken").description("Mockly refresh token (7일 유효"),
                                fieldWithPath("expiresIn").description("Access token 만료 시간 (ms)"),
                                fieldWithPath("user.id").description("사용자 ID"),
                                fieldWithPath("user.email").description("이메일"),
                                fieldWithPath("user.name").description("이름")
                        )));
    }

}