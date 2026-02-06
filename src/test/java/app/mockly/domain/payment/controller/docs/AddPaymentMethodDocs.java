package app.mockly.domain.payment.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class AddPaymentMethodDocs {

    private static final List<FieldDescriptor> REQUEST_FIELDS = List.of(
            fieldWithPath("billingKey").description("PortOne 빌링키").type(SimpleType.STRING)
    );

    private static final List<FieldDescriptor> RESPONSE_DATA_FIELDS = List.of(
            fieldWithPath("id").description("결제 수단 ID").type(SimpleType.NUMBER),
            fieldWithPath("cardBrand").description("카드 브랜드 (VISA, MASTERCARD 등)").type(SimpleType.STRING),
            fieldWithPath("cardNumber").description("마스킹된 카드 번호 (예: 1234****654****2)").type(SimpleType.STRING),
            fieldWithPath("isDefault").description("기본 결제 수단 여부").type(SimpleType.BOOLEAN),
            fieldWithPath("createdAt").description("등록 일시").type(SimpleType.STRING)
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .summary("결제 수단 추가")
                .description("PortOne 빌링키를 등록하여 결제 수단을 추가합니다. 첫 번째 결제 수단은 자동으로 기본 결제 수단으로 설정됩니다.")
                .requestFields(REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_DATA_FIELDS))
                .build();
    }
}
