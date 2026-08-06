package app.mockly.domain.auth.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class LoginWithGoogleCodeDocs {

    private static final List<FieldDescriptor> REQUEST_FIELDS = List.of(
            fieldWithPath("code").description("Google Authorization Code").type(SimpleType.STRING),
            fieldWithPath("codeVerifier").description("PKCE code verifier").type(SimpleType.STRING),
            fieldWithPath("redirectUri").description("OAuth redirect URI").type(SimpleType.STRING),
            fieldWithPath("deviceInfo").description("디바이스 정보").type(JsonFieldType.OBJECT),
            fieldWithPath("deviceInfo.deviceId").description("디바이스 고유 ID").type(SimpleType.STRING),
            fieldWithPath("deviceInfo.deviceName").description("디바이스 이름").type(SimpleType.STRING),
            fieldWithPath("locationInfo").description("위치 정보 (선택)").type(JsonFieldType.OBJECT).optional(),
            fieldWithPath("locationInfo.latitude").description("위도").type(SimpleType.NUMBER).optional(),
            fieldWithPath("locationInfo.longitude").description("경도").type(SimpleType.NUMBER).optional()
    );

    private static final List<FieldDescriptor> RESPONSE_DATA_FIELDS = List.of(
            fieldWithPath("accessToken").description("Mockly JWT access token (15분 유효)").type(SimpleType.STRING),
            fieldWithPath("refreshToken").description("Mockly refresh token (7일 유효)").type(SimpleType.STRING),
            fieldWithPath("expiresIn").description("Access token 만료 시간 (ms)").type(SimpleType.NUMBER),
            fieldWithPath("user").description("사용자 정보").type(JsonFieldType.OBJECT),
            fieldWithPath("user.id").description("사용자 ID").type(SimpleType.STRING),
            fieldWithPath("user.email").description("이메일").type(SimpleType.STRING),
            fieldWithPath("user.name").description("이름").type(SimpleType.STRING)
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .summary("Google 소셜 로그인 (OAuth 2.1)")
                .description("OAuth 2.1 + PKCE를 사용한 Google 소셜 로그인")
                .requestFields(REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_DATA_FIELDS))
                .build();
    }
}
