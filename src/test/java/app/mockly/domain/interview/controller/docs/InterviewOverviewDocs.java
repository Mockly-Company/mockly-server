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

public class InterviewOverviewDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<FieldDescriptor> RESPONSE_FIELDS = List.of(
            fieldWithPath("summary.periodStart").description("현재 주간 이용기간 시작 시각(KST)").type(SimpleType.STRING),
            fieldWithPath("summary.nextResetAt").description("다음 주간 이용기간 시작 시각(KST)").type(SimpleType.STRING),
            fieldWithPath("summary.completedCount").description("현재 주간에 마지막 답변까지 제출한 면접 수").type(SimpleType.NUMBER),
            fieldWithPath("summary.totalPracticeSeconds").description("현재 주간 완료 면접의 총 연습시간(초)").type(SimpleType.NUMBER),
            fieldWithPath("score.latest").description("종료 시각이 가장 최근인 피드백 완료 면접의 종합 점수, 없으면 null").type(SimpleType.NUMBER).optional(),
            fieldWithPath("score.change").description("종료 순서상 최근 점수와 직전 점수의 차이, 비교 점수가 없으면 null").type(SimpleType.NUMBER).optional(),
            fieldWithPath("recentInterviews").description("종료 시각 기준 최근 면접 최대 3건").type(JsonFieldType.ARRAY),
            fieldWithPath("recentInterviews[].sessionId").description("세션 ID").type(SimpleType.STRING).optional(),
            fieldWithPath("recentInterviews[].position").description("지원 포지션").type(SimpleType.STRING).optional(),
            fieldWithPath("recentInterviews[].experienceLevel").description("경력 수준").type(SimpleType.STRING).optional(),
            fieldWithPath("recentInterviews[].interviewType").description("면접 유형").type(SimpleType.STRING).optional(),
            fieldWithPath("recentInterviews[].totalQuestions").description("총 질문 개수").type(SimpleType.NUMBER).optional(),
            fieldWithPath("recentInterviews[].status").description("세션 상태 (FEEDBACK_PENDING, COMPLETED, ABANDONED)").type(SimpleType.STRING).optional(),
            fieldWithPath("recentInterviews[].createdAt").description("세션 생성 시각").type(SimpleType.STRING).optional(),
            fieldWithPath("recentInterviews[].endedAt").description("면접 종료 시각").type(SimpleType.STRING).optional(),
            fieldWithPath("recentInterviews[].durationSeconds").description("면접 연습 시간(초)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("recentInterviews[].overallScore").description("종합 점수, 피드백 완료 전이면 null").type(SimpleType.NUMBER).optional(),
            fieldWithPath("recentInterviews[].feedbackStatus").description("피드백 상태, 생성 전이면 null").type(SimpleType.STRING).optional(),
            fieldWithPath("nextPracticePoint").description("현재 Pro 사용자용 면접 종료 순서상 최근 유효 연습 포인트, 없거나 Free/Basic이면 null")
                    .type(SimpleType.STRING).optional()
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .summary("면접 Overview 조회")
                .description("현재 주간 활동 요약, 최근 점수 변화, 최근 면접과 Pro 연습 포인트를 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters empty() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters subscriptionUnpaid() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .responseFields(ApiResponseDocs.errorResponse("SUBSCRIPTION_UNPAID"))
                .build();
    }
}
