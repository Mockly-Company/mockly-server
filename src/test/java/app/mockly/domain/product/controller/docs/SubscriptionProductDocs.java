package app.mockly.domain.product.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.HeaderDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class SubscriptionProductDocs {

    private static final List<HeaderDescriptorWithType> OPTIONAL_AUTH_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken} (선택)").optional()
    );

    private static final List<FieldDescriptor> PRODUCTS_RESPONSE_FIELDS = List.of(
            fieldWithPath("products").description("구독 상품 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("products[].id").description("상품 ID").type(JsonFieldType.NUMBER),
            fieldWithPath("products[].name").description("상품명").type(SimpleType.STRING),
            fieldWithPath("products[].description").description("상품 설명").type(SimpleType.STRING),
            fieldWithPath("products[].features").description("기능 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("products[].plans").description("요금제 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("products[].plans[].id").description("플랜 ID").type(JsonFieldType.NUMBER),
            fieldWithPath("products[].plans[].price").description("가격").type(SimpleType.NUMBER),
            fieldWithPath("products[].plans[].currency").description("통화").type(SimpleType.STRING),
            fieldWithPath("products[].plans[].billingCycle").description("결제 주기").type(SimpleType.STRING),
            fieldWithPath("products[].plans[].isActive").description("현재 구독 여부").type(SimpleType.BOOLEAN)
    );

    public static ResourceSnippetParameters getProductsNoAuth() {
        return ResourceSnippetParameters.builder()
                .tag("Subscription Product")
                .summary("구독 상품 목록 조회 (비로그인)")
                .description("구독 가능한 상품 목록을 조회합니다. 비로그인 시 모든 플랜의 isActive가 false입니다.")
                .responseFields(ApiResponseDocs.withDataFields(PRODUCTS_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters getProductsAuthNoSubscription() {
        return ResourceSnippetParameters.builder()
                .tag("Subscription Product")
                .summary("구독 상품 목록 조회 (로그인, 구독 없음)")
                .description("로그인 상태이지만 활성 구독이 없는 경우, 모든 플랜의 isActive가 false입니다.")
                .requestHeaders(OPTIONAL_AUTH_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(PRODUCTS_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters getProductsAuthWithSubscription() {
        return ResourceSnippetParameters.builder()
                .tag("Subscription Product")
                .summary("구독 상품 목록 조회 (로그인, 구독 있음)")
                .description("로그인 상태이고 활성 구독이 있는 경우, 해당 플랜의 isActive가 true입니다.")
                .requestHeaders(OPTIONAL_AUTH_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(PRODUCTS_RESPONSE_FIELDS))
                .build();
    }
}
