package app.mockly.domain.auth.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class LoginWithAppleCodeDocs {

    private static final List<FieldDescriptor> REQUEST_FIELDS = List.of(
            fieldWithPath("code").description("Apple Authorization Code").type(SimpleType.STRING),
            fieldWithPath("codeVerifier").description("PKCE code verifier").type(SimpleType.STRING),
            fieldWithPath("redirectUri").description("OAuth redirect URI").type(SimpleType.STRING),
            fieldWithPath("deviceInfo").description("디바이스 정보").type(JsonFieldType.OBJECT),
            fieldWithPath("deviceInfo.deviceId").description("디바이스 고유 ID").type(SimpleType.STRING),
            fieldWithPath("deviceInfo.deviceName").description("디바이스 이름").type(SimpleType.STRING),
            fieldWithPath("locationInfo").description("위치 정보 (선택)").type(JsonFieldType.OBJECT).optional(),
            fieldWithPath("locationInfo.latitude").description("위도").type(SimpleType.NUMBER).optional(),
            fieldWithPath("locationInfo.longitude").description("경도").type(SimpleType.NUMBER).optional(),
            fieldWithPath("fullName").description("Apple 최초 인증 시 전달되는 사용자 이름 (선택)").type(SimpleType.STRING).optional()
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .summary("Apple 소셜 로그인 (OAuth 2.1)")
                .description("OAuth 2.1 + PKCE를 사용한 Apple 소셜 로그인. 신규 회원 생성 시 Free 기본 구독이 자동 배정됩니다.")
                .requestFields(REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(LoginWithGoogleCodeDocs.LOGIN_RESPONSE_DATA_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters invalidToken() {
        return ResourceSnippetParameters.builder()
                .requestFields(REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.errorResponse("에러 코드 (INVALID_APPLE_TOKEN)"))
                .build();
    }
}
