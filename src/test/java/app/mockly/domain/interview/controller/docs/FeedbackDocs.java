package app.mockly.domain.interview.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.HeaderDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;
import java.util.stream.Stream;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class FeedbackDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<FieldDescriptor> RESPONSE_FIELDS = Stream.concat(List.of(
            fieldWithPath("feedbackStatus").description("피드백 상태 (PENDING | GENERATING | COMPLETED | FAILED)").type(SimpleType.STRING),
            fieldWithPath("feedback").description("피드백 데이터 (COMPLETED 시)").type(JsonFieldType.OBJECT).optional(),
            fieldWithPath("message").description("오류 메시지 (FAILED 시)").type(SimpleType.STRING).optional()
    ).stream(), StructuredFeedbackDocs.fields("feedback").stream()).toList();

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .summary("면접 피드백 조회")
                .description("면접 세션의 피드백 상태와 데이터를 조회합니다. COMPLETED이면 피드백 데이터 포함, PENDING/GENERATING이면 202, FAILED이면 오류 메시지 포함.")
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
