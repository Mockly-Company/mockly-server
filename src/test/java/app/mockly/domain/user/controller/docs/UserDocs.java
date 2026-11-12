package app.mockly.domain.user.controller.docs;

import app.mockly.common.ApiResponseDocs;
import app.mockly.domain.auth.controller.docs.AuthMeDocs;
import com.epages.restdocs.apispec.HeaderDescriptorWithType;
import com.epages.restdocs.apispec.ParameterDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class UserDocs {

    private static final List<HeaderDescriptorWithType> AUTH_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<FieldDescriptor> SAVE_AGREEMENTS_REQUEST_FIELDS = List.of(
            fieldWithPath("agreements").description("약관 동의 항목 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("agreements[].type").description("약관 유형 (age14, terms, privacy, location, marketing)").type(SimpleType.STRING),
            fieldWithPath("agreements[].agreed").description("동의 여부").type(SimpleType.BOOLEAN)
    );

    private static final List<FieldDescriptor> UPDATE_PROFILE_REQUEST_FIELDS = List.of(
            fieldWithPath("name").description("닉네임 (1~50자)").type(SimpleType.STRING),
            fieldWithPath("jobCategory").description("희망 직군 (예: DEVELOPER)").type(SimpleType.STRING).optional(),
            fieldWithPath("jobRole").description("희망 직무 (예: FRONTEND)").type(SimpleType.STRING).optional(),
            fieldWithPath("experienceYears").description("경력 연차 (0~40)").type(SimpleType.NUMBER).optional()
    );

    private static final List<ParameterDescriptorWithType> WITHDRAW_PATH_PARAMS = List.of(
            parameterWithName("userId").description("탈퇴할 사용자 UUID").type(SimpleType.STRING)
    );

    public static ResourceSnippetParameters saveAgreementsSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("User")
                .summary("약관 동의 이력 저장")
                .description("필수 약관(age14, terms, privacy) 동의 여부를 검증하고 사용자 동의 이력을 저장합니다.")
                .requestHeaders(AUTH_HEADERS)
                .requestFields(SAVE_AGREEMENTS_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.noContentFields())
                .build();
    }

    public static ResourceSnippetParameters saveAgreementsValidationError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("User")
                .requestFields(SAVE_AGREEMENTS_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.errorResponse("에러 코드 (VALIDATION_ERROR)"))
                .build();
    }

    public static ResourceSnippetParameters updateMyProfileSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("User")
                .summary("내 프로필 기본 정보 수정")
                .description("닉네임, 희망 직군/직무, 경력 연차를 수정하고 갱신된 프로필을 반환합니다.")
                .requestHeaders(AUTH_HEADERS)
                .requestFields(UPDATE_PROFILE_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(AuthMeDocs.USER_INFO_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters updateMyProfileValidationError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("User")
                .requestFields(UPDATE_PROFILE_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.errorResponse("에러 코드 (VALIDATION_ERROR)"))
                .build();
    }

    public static ResourceSnippetParameters withdrawSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("User")
                .summary("회원 탈퇴")
                .description("본인 확인 후 활성 구독 예약 스케줄 취소 및 구독 해지, 결제수단 비활성화, 전체 세션·리프레시 토큰 삭제, 액세스 토큰 블랙리스트 등록 및 계정 비식별화를 수행합니다.")
                .requestHeaders(AUTH_HEADERS)
                .pathParameters(WITHDRAW_PATH_PARAMS)
                .responseFields(ApiResponseDocs.noContentFields())
                .build();
    }

    public static ResourceSnippetParameters withdrawForbidden() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("User")
                .pathParameters(WITHDRAW_PATH_PARAMS)
                .responseFields(ApiResponseDocs.errorResponse("에러 코드 (FORBIDDEN)"))
                .build();
    }
}
