package app.mockly.domain.appconfig.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.HeaderDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class AppConfigDocs {

    private static final List<HeaderDescriptorWithType> CLIENT_HEADERS = List.of(
            headerWithName("X-Client-Platform").description("클라이언트 플랫폼 (ANDROID, IOS)"),
            headerWithName("X-Client-Build").description("버전 비교용 정수 build number"),
            headerWithName("X-Client-Version").description("표시 및 로그 확인용 앱 버전")
    );

    private static final List<FieldDescriptor> RESPONSE_FIELDS = List.of(
            fieldWithPath("update.status").description("업데이트 상태 (NONE, RECOMMENDED, REQUIRED)")
                    .type(SimpleType.STRING),
            fieldWithPath("update.latestVersion").description("해당 플랫폼의 최신 앱 버전")
                    .type(SimpleType.STRING),
            fieldWithPath("update.storeUrl").description("해당 플랫폼의 앱 스토어 URL")
                    .type(SimpleType.STRING),
            fieldWithPath("maintenance.active").description("서비스 점검 활성 여부")
                    .type(SimpleType.BOOLEAN),
            fieldWithPath("maintenance.message").description("점검 안내 메시지")
                    .type(SimpleType.STRING).optional(),
            fieldWithPath("maintenance.startsAt").description("점검 시작 시각 (ISO 8601)")
                    .type(SimpleType.STRING).optional(),
            fieldWithPath("maintenance.endsAt").description("점검 예상 종료 시각 (ISO 8601)")
                    .type(SimpleType.STRING).optional()
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .tag("App Status")
                .summary("앱 버전 호환성 및 점검 상태 조회")
                .description("앱 시작 시 플랫폼과 build number를 기준으로 서버가 업데이트 상태를 계산합니다.")
                .requestHeaders(CLIENT_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters invalidClientInfo() {
        return ResourceSnippetParameters.builder()
                .responseFields(ApiResponseDocs.errorResponse("INVALID_CLIENT_INFO"))
                .build();
    }
}
