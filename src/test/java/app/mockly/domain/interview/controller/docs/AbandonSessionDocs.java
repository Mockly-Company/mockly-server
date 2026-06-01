package app.mockly.domain.interview.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.HeaderDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;

public class AbandonSessionDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .summary("면접 세션 포기")
                .description("진행 중인 면접 세션을 포기 처리합니다. 이미 완료되거나 포기된 세션에는 사용할 수 없습니다.")
                .requestHeaders(REQUEST_HEADERS)
                .responseFields(ApiResponseDocs.noContentFields())
                .build();
    }

    public static ResourceSnippetParameters alreadyCompleted() {
        return ResourceSnippetParameters.builder()
                .tag("Interview")
                .responseFields(ApiResponseDocs.errorResponse("VALIDATION_ERROR"))
                .build();
    }
}
