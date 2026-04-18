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
            fieldWithPath("dailyLimit").description("플랜별 일일 허용 세션 수").type(SimpleType.NUMBER),
            fieldWithPath("usedToday").description("오늘 사용한 세션 수").type(SimpleType.NUMBER),
            fieldWithPath("remaining").description("오늘 남은 세션 수").type(SimpleType.NUMBER),
            fieldWithPath("maxQuestionsPerSession").description("플랜별 세션당 최대 질문 수").type(SimpleType.NUMBER)
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .summary("면접 쿼터 조회")
                .description("현재 플랜의 일일 면접 쿼터와 오늘 사용 현황을 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_FIELDS))
                .build();
    }
}
