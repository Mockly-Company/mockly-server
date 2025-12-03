package app.mockly.domain.auth.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class RefreshTokenDocs {

    private static final List<FieldDescriptor> REQUEST_FIELDS = List.of(
            fieldWithPath("refreshToken").description("Refresh Token (7일 유효)").type(SimpleType.STRING),
            fieldWithPath("locationInfo").description("위치 정보 (선택)").type(JsonFieldType.OBJECT).optional(),
            fieldWithPath("locationInfo.latitude").description("위도").type(SimpleType.NUMBER).optional(),
            fieldWithPath("locationInfo.longitude").description("경도").type(SimpleType.NUMBER).optional()
    );

    private static final List<FieldDescriptor> RESPONSE_DATA_FIELDS = List.of(
            fieldWithPath("accessToken").description("Mockly JWT access token (15분 유효)").type(SimpleType.STRING),
            fieldWithPath("refreshToken").description("Mockly refresh token (7일 유효)").type(SimpleType.STRING),
            fieldWithPath("expiresIn").description("Access token 만료 시간 (ms)").type(SimpleType.NUMBER)
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .summary("Access Token 갱신")
                .description("Refresh Token으로 Access Token을 갱신합니다. Token Rotation이 적용되어 Access Token과 Refresh Token 모두 재발급됩니다.")
                .requestHeaders(
                        headerWithName("X-Device-Id").description("디바이스 고유 ID")
                )
                .requestFields(REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_DATA_FIELDS))
                .build();
    }
}
