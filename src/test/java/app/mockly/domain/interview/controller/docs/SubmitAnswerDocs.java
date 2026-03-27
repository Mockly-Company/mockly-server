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

public class SubmitAnswerDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<FieldDescriptor> REQUEST_FIELDS = List.of(
            fieldWithPath("content").description("답변 내용 (최대 1500자)").type(SimpleType.STRING)
    );

    private static final List<FieldDescriptor> RESPONSE_FIELDS = List.of(
            fieldWithPath("sessionId").description("면접 세션 ID").type(SimpleType.STRING),
            fieldWithPath("currentQuestionNumber").description("현재 질문 번호").type(SimpleType.NUMBER),
            fieldWithPath("totalQuestions").description("총 질문 개수").type(SimpleType.NUMBER),
            fieldWithPath("isCompleted").description("면접 완료 여부").type(SimpleType.BOOLEAN),
            fieldWithPath("nextQuestion").description("다음 질문 (완료 시 null)").type(SimpleType.STRING).optional(),
            fieldWithPath("closingMessage").description("마무리 멘트 (완료 시)").type(SimpleType.STRING).optional(),
            fieldWithPath("feedback").description("피드백 (완료 시)").type(JsonFieldType.OBJECT).optional(),
            fieldWithPath("feedback.overallScore").description("면접 종합 점수 (1~100)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("feedback.expertFeedbacks").description("전문가별 평가 목록").type(JsonFieldType.ARRAY).optional(),
            fieldWithPath("feedback.expertFeedbacks[].expertRole").description("전문가 역할").type(SimpleType.STRING).optional(),
            fieldWithPath("feedback.expertFeedbacks[].score").description("전문가 평가 점수 (1~100)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("feedback.expertFeedbacks[].evaluation").description("전문가 평가 내용").type(SimpleType.STRING).optional(),
            fieldWithPath("feedback.strengths").description("전반적인 강점 요약").type(SimpleType.STRING).optional(),
            fieldWithPath("feedback.improvements").description("전반적인 개선점 요약").type(SimpleType.STRING).optional(),
            fieldWithPath("feedback.detailedAnalysis").description("질문별 상세 분석 (PRO 전용)").type(SimpleType.STRING).optional()
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .summary("답변 제출")
                .description("면접 질문에 대한 답변을 제출합니다. 마지막 답변이면 피드백을 반환하고, 아니면 다음 질문을 반환합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .requestFields(REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters successCompleted() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .requestHeaders(REQUEST_HEADERS)
                .requestFields(REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters alreadyCompleted() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .responseFields(ApiResponseDocs.errorResponse("VALIDATION_ERROR"))
                .build();
    }

    public static ResourceSnippetParameters notFound() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }
}
