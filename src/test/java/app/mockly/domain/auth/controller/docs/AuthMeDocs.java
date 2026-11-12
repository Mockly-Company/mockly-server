package app.mockly.domain.auth.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.HeaderDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class AuthMeDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken} - 로그인 시 발급받은 JWT")
    );

    public static final List<FieldDescriptor> USER_INFO_FIELDS = List.of(
            fieldWithPath("id").description("사용자 ID").type(SimpleType.STRING),
            fieldWithPath("email").description("이메일").type(SimpleType.STRING),
            fieldWithPath("name").description("닉네임(이름)").type(SimpleType.STRING),
            fieldWithPath("hasAgreedToTerms").description("필수 약관 동의 완료 여부").type(SimpleType.BOOLEAN),
            fieldWithPath("jobCategory").description("희망 직군 (예: DEVELOPER)").type(SimpleType.STRING).optional(),
            fieldWithPath("jobRole").description("희망 직무 (예: BACKEND)").type(SimpleType.STRING).optional(),
            fieldWithPath("experienceYears").description("경력 연차 (0~40)").type(SimpleType.NUMBER).optional()
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .summary("현재 사용자 조회")
                .description("JWT 기반 인증으로 현재 로그인한 사용자 프로필 및 약관 동의 여부를 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(USER_INFO_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters noToken() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .responseFields(ApiResponseDocs.errorResponse("에러 코드"))
                .build();
    }

    public static ResourceSnippetParameters invalidToken() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .responseFields(ApiResponseDocs.errorResponse("에러 코드 (INVALID_TOKEN)"))
                .build();
    }

    public static ResourceSnippetParameters userNotFound() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .responseFields(ApiResponseDocs.errorResponse("에러 코드 (USER_NOT_FOUND)"))
                .build();
    }
}
