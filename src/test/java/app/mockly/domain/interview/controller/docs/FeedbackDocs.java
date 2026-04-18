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

public class FeedbackDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<FieldDescriptor> RESPONSE_FIELDS = List.of(
            fieldWithPath("overallScore").description("종합 점수 (1~100)").type(SimpleType.NUMBER),
            fieldWithPath("expertFeedbacks").description("전문가별 평가 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("expertFeedbacks[].expertRole").description("전문가 역할").type(SimpleType.STRING),
            fieldWithPath("expertFeedbacks[].score").description("전문가 평가 점수 (1~100)").type(SimpleType.NUMBER),
            fieldWithPath("expertFeedbacks[].evaluation").description("전문가 평가 내용").type(SimpleType.STRING),
            fieldWithPath("strengths").description("전반적인 강점").type(SimpleType.STRING),
            fieldWithPath("improvements").description("전반적인 개선점").type(SimpleType.STRING),
            fieldWithPath("detailedAnalysis").description("질문별 상세 분석 (PRO 플랜 전용)").type(SimpleType.STRING).optional()
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .summary("면접 피드백 조회")
                .description("완료된 면접 세션의 AI 피드백을 조회합니다. 진행 중이거나 포기된 세션은 조회할 수 없습니다.")
                .requestHeaders(REQUEST_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters notFound() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }
}
