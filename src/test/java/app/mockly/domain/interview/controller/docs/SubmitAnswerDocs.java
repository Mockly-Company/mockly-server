package app.mockly.domain.interview.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.HeaderDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;

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
            fieldWithPath("sessionStatus").description("세션 상태 (IN_PROGRESS | FEEDBACK_PENDING)").type(SimpleType.STRING)
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .summary("답변 제출")
                .description("면접 질문에 대한 답변을 제출합니다. 마지막 답변이면 FEEDBACK_PENDING 상태를 반환하고 피드백은 비동기로 생성됩니다. 피드백 완료는 GET /{sessionId}/feedback/events SSE로 수신합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .requestFields(REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters feedbackPending() {
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
