package app.mockly.domain.payment.controller.docs;

import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class GetPaymentMethodsDocs {

    private static final List<FieldDescriptor> RESPONSE_FIELDS = List.of(
            fieldWithPath("success").description("성공 여부").type(SimpleType.BOOLEAN),
            fieldWithPath("data").description("결제 수단 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("data[].id").description("결제 수단 ID").type(SimpleType.NUMBER).optional(),
            fieldWithPath("data[].cardBrand").description("카드 브랜드 (VISA, MASTERCARD 등)").type(SimpleType.STRING).optional(),
            fieldWithPath("data[].cardNumber").description("마스킹된 카드 번호 (예: 1234****654****2)").type(SimpleType.STRING).optional(),
            fieldWithPath("data[].isDefault").description("기본 결제 수단 여부").type(SimpleType.BOOLEAN).optional(),
            fieldWithPath("data[].createdAt").description("등록 일시").type(SimpleType.STRING).optional(),
            fieldWithPath("error").description("에러 코드 (성공 시 null)").type(SimpleType.STRING).optional(),
            fieldWithPath("message").description("응답 메시지 (성공 시 null)").type(SimpleType.STRING).optional(),
            fieldWithPath("timestamp").description("응답 타임스탬프 (Unix timestamp, ms)").type(SimpleType.NUMBER)
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .summary("결제 수단 목록 조회")
                .description("사용자의 등록된 결제 수단 목록을 조회합니다. 활성(isActive=true) 상태의 결제 수단만 반환하며, 등록일 역순으로 정렬됩니다. 등록된 결제 수단이 없으면 빈 배열을 반환합니다.")
                .responseFields(RESPONSE_FIELDS)
                .build();
    }
}
