package app.mockly.domain.interview.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.HeaderDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class QuotaDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<FieldDescriptor> RESPONSE_FIELDS = List.of(
            fieldWithPath("periodStart").description("현재 주간 이용기간 시작 시각(KST)").type(SimpleType.STRING),
            fieldWithPath("nextResetAt").description("다음 주간 한도 리셋 시각(KST)").type(SimpleType.STRING),
            fieldWithPath("maxQuestions").description("플랜별 세션당 최대 질문 수").type(SimpleType.NUMBER),
            fieldWithPath("interview.limit").description("주간 면접 한도").type(SimpleType.NUMBER),
            fieldWithPath("interview.used").description("현재 주간 면접 사용량").type(SimpleType.NUMBER),
            fieldWithPath("interview.remaining").description("현재 주간 면접 잔여량").type(SimpleType.NUMBER),
            fieldWithPath("improvementPractice.limit").description("주간 개선 연습 한도").type(SimpleType.NUMBER),
            fieldWithPath("improvementPractice.used").description("현재 주간 개선 연습 사용량").type(SimpleType.NUMBER),
            fieldWithPath("improvementPractice.remaining").description("현재 주간 개선 연습 잔여량").type(SimpleType.NUMBER)
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .summary("면접 쿼터 조회")
                .description("현재 주간 이용기간의 면접·개선 연습 쿼터를 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
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
