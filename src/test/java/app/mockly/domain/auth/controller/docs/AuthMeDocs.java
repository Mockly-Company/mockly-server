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

    private static final List<FieldDescriptor> RESPONSE_DATA_FIELDS = List.of(
            fieldWithPath("id").description("사용자 ID").type(SimpleType.STRING),
            fieldWithPath("email").description("이메일").type(SimpleType.STRING),
            fieldWithPath("name").description("이름").type(SimpleType.STRING)
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .summary("현재 사용자 조회")
                .description("JWT 기반 인증으로 현재 로그인한 사용자 정보를 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_DATA_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters noToken() {
        return ResourceSnippetParameters.builder()
                .responseFields(ApiResponseDocs.errorResponse("에러 코드"))
                .build();
    }

    public static ResourceSnippetParameters invalidToken() {
        return ResourceSnippetParameters.builder()
                .responseFields(ApiResponseDocs.errorResponse("에러 코드 (INVALID_TOKEN)"))
                .build();
    }

    public static ResourceSnippetParameters userNotFound() {
        return ResourceSnippetParameters.builder()
                .responseFields(ApiResponseDocs.errorResponse("에러 코드 (USER_NOT_FOUND)"))
                .build();
    }
}
