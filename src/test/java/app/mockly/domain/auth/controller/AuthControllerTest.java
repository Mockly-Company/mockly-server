package app.mockly.domain.auth.controller;

import app.mockly.domain.auth.dto.GoogleUser;
import app.mockly.domain.auth.dto.request.AuthorizationCodeRequest;
import app.mockly.domain.auth.entity.OAuth2Provider;
import app.mockly.domain.auth.entity.User;
import app.mockly.domain.auth.repository.UserRepository;
import app.mockly.domain.auth.service.AuthService;
import app.mockly.domain.auth.service.GoogleOAuthService;
import app.mockly.domain.auth.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import org.springframework.restdocs.payload.JsonFieldType;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional // 테스트 후 롤백
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @MockitoBean // Google OAuth만 모킹
    private GoogleOAuthService googleOAuthService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private String validAccessToken;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .name("테스트 사용자")
                .provider(OAuth2Provider.GOOGLE)
                .providerId("test-google-provider-id")
                .build();
        testUser = userRepository.save(testUser);  // save() 반환값 사용 (ID가 생성됨)

        validAccessToken = jwtService.generateAccessToken(testUser.getId());
    }

    @Test
    @DisplayName("POST /api/auth/login/google/code - Google OAuth 로그인")
    void loginWithGoogleCode() throws Exception {
        GoogleUser mockGoogleUser = new GoogleUser(
            "google-user-id-123",
            "user@mockly.com",
            "Mockly User",
            true
        );

        given(googleOAuthService.exchangeAuthorizationCode(anyString(), anyString(), anyString()))
                .willReturn("mock-id-token");
        given(googleOAuthService.verifyIdToken(anyString()))
                .willReturn(mockGoogleUser);

        AuthorizationCodeRequest request = new AuthorizationCodeRequest(
            "test-auth-code",
            "test-code-verifier",
            "https://mockly.app/oauth2/google/redirect"
        );

        // when & then
        mockMvc.perform(post("/api/auth/login/google/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.user.email").value("user@mockly.com"))
                .andExpect(jsonPath("$.data.user.name").value("Mockly User"))
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
                                fieldWithPath("data.user.id").type(JsonFieldType.STRING).description("사용자 ID (UUID)"),
                                fieldWithPath("data.user.email").type(JsonFieldType.STRING).description("이메일"),
                                fieldWithPath("data.user.name").type(JsonFieldType.STRING).description("이름"),
                                fieldWithPath("error").type(JsonFieldType.NULL).description("에러 코드 (성공 시 null)"),
                                fieldWithPath("message").type(JsonFieldType.NULL).description("응답 메시지 (성공 시 null)"),
                                fieldWithPath("timestamp").type(JsonFieldType.NUMBER).description("응답 타임스탬프 (Unix timestamp, ms)")
                        )));
    }

    @Test
    @DisplayName("GET /api/auth/me - 성공: 유효한 JWT로 현재 사용자 조회")
    void getCurrentUser_Success() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                    .header("Authorization", "Bearer " + validAccessToken)
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.name").value("테스트 사용자"))
                .andDo(document("auth-me-success",
                        requestHeaders(
                                headerWithName("Authorization")
                                        .description("Bearer {accessToken} - 로그인 시 발급받은 JWT")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부 (true)"),
                                fieldWithPath("data").type(JsonFieldType.OBJECT).description("사용자 정보"),
                                fieldWithPath("data.id").type(JsonFieldType.STRING).description("사용자 ID (UUID)"),
                                fieldWithPath("data.email").type(JsonFieldType.STRING).description("이메일"),
                                fieldWithPath("data.name").type(JsonFieldType.STRING).description("이름"),
                                fieldWithPath("error").type(JsonFieldType.NULL).description("에러 코드 (성공 시 null)"),
                                fieldWithPath("message").type(JsonFieldType.NULL).description("메시지 (성공 시 null)"),
                                fieldWithPath("timestamp").type(JsonFieldType.NUMBER).description("응답 타임스탬프")
                        )
                ));
    }

    @Test
    @DisplayName("GET /api/auth/me - 실패: Authorization 헤더 없음")
    void getCurrentUser_NoToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andDo(document("auth-me-no-token",
                responseFields(
                    fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부 (false)"),
                    fieldWithPath("data").type(JsonFieldType.NULL).description("응답 데이터 (에러 시 null)"),
                    fieldWithPath("error").type(JsonFieldType.STRING).description("에러 코드"),
                    fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                    fieldWithPath("timestamp").type(JsonFieldType.NUMBER).description("응답 타임스탬프")
                )
            ));
    }

    @Test
    @DisplayName("GET /api/auth/me - 실패: 잘못된 JWT 토큰")
    void getCurrentUser_InvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer invalid.token.here")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("INVALID_TOKEN"))
            .andDo(document("auth-me-invalid-token",
                requestHeaders(
                    headerWithName("Authorization").description("잘못된 형식의 JWT")
                ),
                responseFields(
                    fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부 (false)"),
                    fieldWithPath("data").type(JsonFieldType.NULL).description("응답 데이터 (에러 시 null)"),
                    fieldWithPath("error").type(JsonFieldType.STRING).description("에러 코드 (INVALID_TOKEN)"),
                    fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                    fieldWithPath("timestamp").type(JsonFieldType.NUMBER).description("응답 타임스탬프")
                )
            ));
    }

    @Test
    @DisplayName("GET /api/auth/me - 실패: 존재하지 않는 사용자")
    void getCurrentUser_UserNotFound() throws Exception {
        UUID ghostUserId = UUID.randomUUID();
        String tokenWithNonExistentUser = jwtService.generateAccessToken(ghostUserId);

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + tokenWithNonExistentUser)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"))
            .andDo(document("auth-me-user-not-found",
                requestHeaders(
                    headerWithName("Authorization").description("유효하지만 DB에 존재하지 않는 사용자의 JWT")
                ),
                responseFields(
                    fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부 (false)"),
                    fieldWithPath("data").type(JsonFieldType.NULL).description("응답 데이터 (에러 시 null)"),
                    fieldWithPath("error").type(JsonFieldType.STRING).description("에러 코드 (USER_NOT_FOUND)"),
                    fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                    fieldWithPath("timestamp").type(JsonFieldType.NUMBER).description("응답 타임스탬프")
                )
            ));
    }
}