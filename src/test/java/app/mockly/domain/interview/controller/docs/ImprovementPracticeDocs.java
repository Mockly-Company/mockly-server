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

public class ImprovementPracticeDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<FieldDescriptor> CREATE_REQUEST_FIELDS = List.of(
            fieldWithPath("improvementId").description("면접 피드백 개선 포인트 ID").type(SimpleType.NUMBER)
    );

    private static final List<FieldDescriptor> SUBMIT_ANSWER_REQUEST_FIELDS = List.of(
            fieldWithPath("answer").description("개선하여 다시 작성한 답변 (1~1500자)").type(SimpleType.STRING)
    );

    private static final List<FieldDescriptor> STATUS_RESPONSE_FIELDS = List.of(
            fieldWithPath("id").description("개선 연습 ID").type(SimpleType.NUMBER),
            fieldWithPath("status").description("개선 연습 상태 (PENDING | GENERATING | COMPLETED | FAILED)").type(SimpleType.STRING)
    );

    private static final List<FieldDescriptor> DETAIL_COMPLETED_RESPONSE_FIELDS = List.of(
            fieldWithPath("id").description("개선 연습 ID").type(SimpleType.NUMBER),
            fieldWithPath("status").description("개선 연습 상태 (PENDING | GENERATING | COMPLETED | FAILED)").type(SimpleType.STRING),
            fieldWithPath("questionNumber").description("대상 면접 질문 번호").type(SimpleType.NUMBER),
            fieldWithPath("questionText").description("대상 면접 질문 원문").type(SimpleType.STRING),
            fieldWithPath("originalAnswer").description("기존 면접 답변 원문").type(SimpleType.STRING),
            fieldWithPath("submittedAnswer").description("제출한 개선 답변 (미제출 시 null)").type(SimpleType.STRING).optional(),
            fieldWithPath("result").description("AI 비교 평가 결과 (COMPLETED 시 포함, 그 외 null)").type(JsonFieldType.OBJECT).optional(),
            fieldWithPath("result.score").description("개선 답변 평가 점수 (1~100)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("result.summary").description("개선 요약 코멘트").type(SimpleType.STRING).optional(),
            fieldWithPath("result.detail").description("기존 답변 대비 구체적 개선 분석").type(SimpleType.STRING).optional(),
            fieldWithPath("result.quote").description("개선 답변 중 핵심 인용구").type(SimpleType.STRING).optional()
    );

    private static final List<FieldDescriptor> DETAIL_NULL_RESULT_RESPONSE_FIELDS = List.of(
            fieldWithPath("id").description("개선 연습 ID").type(SimpleType.NUMBER),
            fieldWithPath("status").description("개선 연습 상태 (GENERATING)").type(SimpleType.STRING),
            fieldWithPath("questionNumber").description("대상 면접 질문 번호").type(SimpleType.NUMBER),
            fieldWithPath("questionText").description("대상 면접 질문 원문").type(SimpleType.STRING),
            fieldWithPath("originalAnswer").description("기존 면접 답변 원문").type(SimpleType.STRING),
            fieldWithPath("submittedAnswer").description("제출한 개선 답변").type(SimpleType.STRING).optional(),
            fieldWithPath("result").description("재시도 시에는 null").type(JsonFieldType.OBJECT).optional()
    );

    public static ResourceSnippetParameters createSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Interview")
                .summary("답변 개선 연습 생성")
                .description("""
                        Pro 플랜 전용 답변 개선 연습 세션을 PENDING 상태로 생성합니다.
                        생성 시점에는 주간 쿼터를 검증만 하고 차감하지 않으며, 실제 답변 제출 시점에 주간 개선 연습 쿼터 1회가 차감됩니다.
                        """)
                .requestHeaders(REQUEST_HEADERS)
                .requestFields(CREATE_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(STATUS_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters createForbidden() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Interview")
                .requestFields(CREATE_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.errorResponse("FORBIDDEN"))
                .build();
    }

    public static ResourceSnippetParameters createQuotaExceeded() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Interview")
                .requestFields(CREATE_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.errorResponse("QUOTA_EXCEEDED"))
                .build();
    }

    public static ResourceSnippetParameters detailSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Interview")
                .summary("답변 개선 연습 단건 조회")
                .description("답변 개선 연습 상태, 원문 질문·답변 스냅샷, 완료된 AI 비교 평가 결과를 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .pathParameters(parameterWithName("practiceId").description("개선 연습 ID"))
                .responseFields(ApiResponseDocs.withDataFields(DETAIL_COMPLETED_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters detailNotFound() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Interview")
                .pathParameters(parameterWithName("practiceId").description("개선 연습 ID"))
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }

    public static ResourceSnippetParameters submitAnswerSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Interview")
                .summary("개선 답변 제출 및 평가 시작")
                .description("""
                        개선 답변을 제출하고 주간 개선 연습 쿼터 1회를 차감한 뒤 비동기 AI 비교 평가를 시작합니다.
                        상태가 GENERATING으로 전이되며 완료 이벤트는 SSE(/events) 또는 단건 조회로 확인할 수 있습니다.
                        """)
                .requestHeaders(REQUEST_HEADERS)
                .pathParameters(parameterWithName("practiceId").description("개선 연습 ID"))
                .requestFields(SUBMIT_ANSWER_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(STATUS_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters submitAnswerQuotaExceeded() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Interview")
                .pathParameters(parameterWithName("practiceId").description("개선 연습 ID"))
                .requestFields(SUBMIT_ANSWER_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.errorResponse("QUOTA_EXCEEDED"))
                .build();
    }

    public static ResourceSnippetParameters submitAnswerValidationError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Interview")
                .pathParameters(parameterWithName("practiceId").description("개선 연습 ID"))
                .requestFields(SUBMIT_ANSWER_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.errorResponse("VALIDATION_ERROR"))
                .build();
    }

    public static ResourceSnippetParameters eventsSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Interview")
                .summary("답변 개선 연습 상태 SSE 구독")
                .description("""
                        답변 개선 연습의 비동기 AI 비교 평가 완료/실패 상태를 SSE(Server-Sent Events)로 수신합니다.

                        이벤트 타입:
                        - status: 평가 상태 전이 (data: {"practiceStatus": "completed" | "failed"})

                        연결 시 이미 COMPLETED 또는 FAILED 상태이면 즉시 status 이벤트를 전송하고 스트림을 종료합니다.
                        """)
                .requestHeaders(REQUEST_HEADERS)
                .build();
    }

    public static ResourceSnippetParameters eventsNotFound() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Interview")
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }

    public static ResourceSnippetParameters retrySuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Interview")
                .summary("실패한 답변 개선 평가 재시도")
                .description("FAILED 상태의 답변 개선 연습을 추가 주간 쿼터 차감 없이 GENERATING 상태로 되돌려 평가를 재실행합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .pathParameters(parameterWithName("practiceId").description("개선 연습 ID"))
                .responseFields(ApiResponseDocs.withDataFields(DETAIL_NULL_RESULT_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters retryInvalidStatus() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Interview")
                .pathParameters(parameterWithName("practiceId").description("개선 연습 ID"))
                .responseFields(ApiResponseDocs.errorResponse("VALIDATION_ERROR"))
                .build();
    }
}
