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

public class SessionDetailDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<FieldDescriptor> RESPONSE_FIELDS = List.of(
            fieldWithPath("sessionId").description("세션 ID").type(SimpleType.STRING),
            fieldWithPath("position").description("지원 포지션").type(SimpleType.STRING),
            fieldWithPath("experienceLevel").description("경력 수준 (JUNIOR, MID, SENIOR)").type(SimpleType.STRING),
            fieldWithPath("interviewType").description("면접 유형 (TECHNICAL, BEHAVIORAL, MIXED)").type(SimpleType.STRING),
            fieldWithPath("totalQuestions").description("총 질문 개수").type(SimpleType.NUMBER),
            fieldWithPath("currentQuestionNumber").description("현재 질문 번호").type(SimpleType.NUMBER),
            fieldWithPath("status").description("세션 상태 (IN_PROGRESS, COMPLETED, ABANDONED)").type(SimpleType.STRING),
            fieldWithPath("createdAt").description("생성 시각 (Unix timestamp)").type(SimpleType.NUMBER),
            fieldWithPath("completedAt").description("완료 시각 (완료 시, Unix timestamp)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("messages").description("면접 대화 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("messages[].role").description("메시지 역할 (INTERVIEWER, USER)").type(SimpleType.STRING),
            fieldWithPath("messages[].content").description("메시지 내용").type(SimpleType.STRING),
            fieldWithPath("messages[].questionNumber").description("질문 번호").type(SimpleType.NUMBER).optional(),
            fieldWithPath("messages[].createdAt").description("메시지 생성 시각 (Unix timestamp)").type(SimpleType.NUMBER),
            fieldWithPath("feedback").description("AI 피드백 (완료된 세션에만 존재)").type(JsonFieldType.OBJECT).optional(),
            fieldWithPath("feedback.overallScore").description("종합 점수 (1~100)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("feedback.expertFeedbacks").description("전문가별 평가 목록").type(JsonFieldType.ARRAY).optional(),
            fieldWithPath("feedback.expertFeedbacks[].expertRole").description("전문가 역할").type(SimpleType.STRING).optional(),
            fieldWithPath("feedback.expertFeedbacks[].score").description("전문가 평가 점수 (1~100)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("feedback.expertFeedbacks[].evaluation").description("전문가 평가 내용").type(SimpleType.STRING).optional(),
            fieldWithPath("feedback.strengths").description("전반적인 강점").type(SimpleType.STRING).optional(),
            fieldWithPath("feedback.improvements").description("전반적인 개선점").type(SimpleType.STRING).optional(),
            fieldWithPath("feedback.detailedAnalysis").description("질문별 상세 분석 (PRO 플랜 전용)").type(SimpleType.STRING).optional()
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .summary("면접 세션 상세 조회")
                .description("특정 면접 세션의 상세 정보와 전체 대화 내역을 조회합니다.")
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
