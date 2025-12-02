package app.mockly.domain.auth.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;

import java.util.List;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class LogoutDocs {

    private static final List<FieldDescriptor> REQUEST_FIELDS = List.of(
            fieldWithPath("refreshToken").description("Refresh Token (7일 유효)").type(SimpleType.STRING)
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .summary("로그아웃")
                .description("현재 기기에서 로그아웃합니다. Access Token은 블랙리스트에 추가되고, Refresh Token은 삭제됩니다. Session은 유지되어 로그인 기록이 보존됩니다.")
                .requestHeaders(
                        headerWithName("Authorization").description("Bearer {accessToken}")
                )
                .requestFields(REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.noContentFields())
                .build();
    }
}
