package app.mockly.domain.payment.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.ResourceSnippetParameters;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;

public class DeletePaymentMethodDocs {

    public static ResourceSnippetParameters success() {
        return ResourceSnippetParameters.builder()
                .summary("결제 수단 삭제")
                .description("등록된 결제 수단을 삭제합니다. 활성 구독이 있는 경우, 기본 결제 수단은 삭제할 수 없으며 구독을 해지하거나 다른 결제 수단을 기본으로 설정한 후 삭제해야 합니다. 일반 결제 수단은 활성 구독이 있어도 삭제 가능합니다. 삭제된 결제 수단은 목록 조회 시 표시되지 않습니다.")
                .pathParameters(
                        parameterWithName("paymentMethodId").description("삭제할 결제 수단 ID")
                )
                .responseFields(ApiResponseDocs.noContentFields())
                .build();
    }
}
