package app.mockly.domain.interview.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.HeaderDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class CreateInterviewDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<FieldDescriptor> REQUEST_FIELDS = List.of(
            fieldWithPath("position").description("지원 포지션 (예: 백엔드 개발자)").type(SimpleType.STRING),
            fieldWithPath("experienceLevel").description("경력 수준 (JUNIOR, SENIOR 등)").type(SimpleType.STRING),
            fieldWithPath("interviewType").description("면접 유형 (TECHNICAL, BEHAVIORAL 등)").type(SimpleType.STRING),
            fieldWithPath("totalQuestions").description("총 질문 개수 (3, 5, 10 중 플랜별 허용 값)").type(SimpleType.NUMBER)
    );

    private static final List<FieldDescriptor> RESPONSE_FIELDS = List.of(
            fieldWithPath("sessionId").description("생성된 면접 세션 ID").type(SimpleType.STRING),
            fieldWithPath("position").description("지원 포지션").type(SimpleType.STRING),
            fieldWithPath("experienceLevel").description("경력 수준").type(SimpleType.STRING),
            fieldWithPath("interviewType").description("면접 유형").type(SimpleType.STRING),
            fieldWithPath("totalQuestions").description("총 질문 개수").type(SimpleType.NUMBER),
            fieldWithPath("currentQuestionNumber").description("현재 질문 번호 (1)").type(SimpleType.NUMBER),
            fieldWithPath("status").description("세션 상태 (IN_PROGRESS)").type(SimpleType.STRING),
            fieldWithPath("firstQuestion").description("AI가 생성한 첫 번째 면접 질문").type(SimpleType.STRING)
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .summary("면접 세션 생성")
                .description("AI 면접 세션을 생성하고 첫 번째 질문을 반환합니다. 플랜별 일일 쿼터와 질문 개수 제한이 적용됩니다.")
                .requestHeaders(REQUEST_HEADERS)
                .requestFields(REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters quotaExceeded() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .responseFields(ApiResponseDocs.errorResponse("QUOTA_EXCEEDED"))
                .build();
    }

    public static ResourceSnippetParameters invalidQuestionCount() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .responseFields(ApiResponseDocs.errorResponse("VALIDATION_ERROR"))
                .build();
    }

    public static ResourceSnippetParameters unauthorized() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .responseFields(ApiResponseDocs.errorResponse("UNAUTHORIZED"))
                .build();
    }
}
