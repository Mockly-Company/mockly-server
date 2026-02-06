package app.mockly.domain.payment.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class SetDefaultPaymentMethodDocs {

    private static final List<FieldDescriptor> RESPONSE_DATA_FIELDS = List.of(
            fieldWithPath("id").description("결제 수단 ID").type(SimpleType.NUMBER),
            fieldWithPath("cardBrand").description("카드 브랜드 (VISA, MASTERCARD 등)").type(SimpleType.STRING),
            fieldWithPath("cardNumber").description("마스킹된 카드 번호 (예: 1234****654****2)").type(SimpleType.STRING),
            fieldWithPath("isDefault").description("기본 결제 수단 여부 (true)").type(SimpleType.BOOLEAN),
            fieldWithPath("createdAt").description("등록 일시").type(SimpleType.STRING)
    );

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .summary("기본 결제 수단 변경")
                .description("지정한 결제 수단을 기본 결제 수단으로 설정합니다. 기존 기본 결제 수단은 자동으로 일반 결제 수단으로 변경됩니다. 이미 기본 결제 수단인 경우 idempotent하게 동작합니다.")
                .pathParameters(
                        parameterWithName("paymentMethodId").description("기본으로 설정할 결제 수단 ID")
                )
                .responseFields(ApiResponseDocs.withDataFields(RESPONSE_DATA_FIELDS))
                .build();
    }
}
