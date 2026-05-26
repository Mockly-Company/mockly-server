package app.mockly.domain.interview.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.HeaderDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class RetryFeedbackDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<FieldDescriptor> RESPONSE_FIELDS = List.of(
            fieldWithPath("feedbackStatus").description("피드백 상태 (PENDING)").type(SimpleType.STRING),
            fieldWithPath("feedback").description("피드백 데이터 (COMPLETED 시)").type(JsonFieldType.OBJECT).optional(),
            fieldWithPath("feedback.overallScore").description("종합 점수 (1~100)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("feedback.expertFeedbacks").description("전문가별 평가 목록").type(JsonFieldType.ARRAY).optional(),
            fieldWithPath("feedback.expertFeedbacks[].expertRole").description("전문가 역할").type(SimpleType.STRING).optional(),
            fieldWithPath("feedback.expertFeedbacks[].score").description("전문가 평가 점수 (1~100)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("feedback.expertFeedbacks[].evaluation").description("전문가 평가 내용").type(SimpleType.STRING).optional(),
            fieldWithPath("feedback.strengths").description("전반적인 강점").type(SimpleType.STRING).optional(),
            fieldWithPath("feedback.improvements").description("전반적인 개선점").type(SimpleType.STRING).optional(),
            fieldWithPath("feedback.detailedAnalysis").description("질문별 상세 분석 (PRO 플랜 전용)").type(SimpleType.STRING).optional(),
            fieldWithPath("message").description("오류 메시지 (FAILED 시)").type(SimpleType.STRING).optional()
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .summary("면접 피드백 재시도")
                .description("실패한 면접 피드백 생성을 다시 요청합니다. FAILED 상태이면서 세션 상태가 FEEDBACK_PENDING인 경우에만 사용할 수 있습니다.")
                .requestHeaders(REQUEST_HEADERS)
                .pathParameters(parameterWithName("sessionId").description("면접 세션 ID"))
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters invalidStatus() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .pathParameters(parameterWithName("sessionId").description("면접 세션 ID"))
                .responseFields(ApiResponseDocs.errorResponse("VALIDATION_ERROR"))
                .build();
    }

    public static ResourceSnippetParameters notFound() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .pathParameters(parameterWithName("sessionId").description("면접 세션 ID"))
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }
}
