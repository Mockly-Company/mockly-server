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

public class SessionListDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<FieldDescriptor> RESPONSE_FIELDS = List.of(
            fieldWithPath("sessions").description("면접 세션 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("sessions[].sessionId").description("세션 ID").type(SimpleType.STRING),
            fieldWithPath("sessions[].position").description("지원 포지션").type(SimpleType.STRING),
            fieldWithPath("sessions[].experienceLevel").description("경력 수준 (JUNIOR, MID, SENIOR)").type(SimpleType.STRING),
            fieldWithPath("sessions[].interviewType").description("면접 유형 (TECHNICAL, BEHAVIORAL, MIXED)").type(SimpleType.STRING),
            fieldWithPath("sessions[].totalQuestions").description("총 질문 개수").type(SimpleType.NUMBER),
            fieldWithPath("sessions[].status").description("세션 상태 (IN_PROGRESS, FEEDBACK_PENDING, COMPLETED, ABANDONED)").type(SimpleType.STRING),
            fieldWithPath("sessions[].createdAt").description("생성 시각 (ISO-8601)").type(SimpleType.STRING),
            fieldWithPath("sessions[].endedAt").description("면접 종료 시각 (진행 중이면 null, ISO-8601)").type(SimpleType.STRING).optional(),
            fieldWithPath("sessions[].durationSeconds").description("면접 연습 시간(초, 진행 중이면 null)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("sessions[].overallScore").description("종합 점수 (피드백 완료 전이면 null)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("sessions[].feedbackStatus").description("피드백 상태 (생성 전이면 null)").type(SimpleType.STRING).optional(),
            fieldWithPath("pagination").description("페이지네이션 정보").type(JsonFieldType.OBJECT),
            fieldWithPath("pagination.page").description("현재 페이지 (1-based)").type(SimpleType.NUMBER),
            fieldWithPath("pagination.size").description("페이지 크기").type(SimpleType.NUMBER),
            fieldWithPath("pagination.totalElements").description("전체 세션 수").type(SimpleType.NUMBER),
            fieldWithPath("pagination.totalPages").description("전체 페이지 수").type(SimpleType.NUMBER),
            fieldWithPath("pagination.isFirst").description("첫 번째 페이지 여부").type(SimpleType.BOOLEAN),
            fieldWithPath("pagination.isLast").description("마지막 페이지 여부").type(SimpleType.BOOLEAN)
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .summary("면접 세션 목록 조회")
                .description("사용자의 면접 세션 목록을 페이지네이션으로 조회합니다. status 파라미터로 상태 필터링이 가능합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters filtered() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .requestHeaders(REQUEST_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_FIELDS))
                .build();
    }
}
