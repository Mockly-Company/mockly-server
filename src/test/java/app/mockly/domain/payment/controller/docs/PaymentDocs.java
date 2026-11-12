package app.mockly.domain.payment.controller.docs;

import app.mockly.common.ApiResponseDocs;
import com.epages.restdocs.apispec.HeaderDescriptorWithType;
import com.epages.restdocs.apispec.ParameterDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

public class PaymentDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<ParameterDescriptorWithType> LIST_QUERY_PARAMETERS = List.of(
            parameterWithName("page").description("페이지 번호 (1-based, 기본값 1)").type(SimpleType.INTEGER).optional(),
            parameterWithName("size").description("페이지 크기 (기본값 10, 최대 20)").type(SimpleType.INTEGER).optional(),
            parameterWithName("status").description("결제 상태 필터 (PENDING, PAID, FAILED, CANCELED)").type(SimpleType.STRING).optional(),
            parameterWithName("startDate").description("조회 시작일 (yyyy-MM-dd)").type(SimpleType.STRING).optional(),
            parameterWithName("endDate").description("조회 종료일 (yyyy-MM-dd)").type(SimpleType.STRING).optional()
    );

    private static final List<FieldDescriptor> LIST_RESPONSE_FIELDS = List.of(
            fieldWithPath("payments").description("결제 내역 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("payments[].id").description("결제 ID").type(SimpleType.STRING),
            fieldWithPath("payments[].name").description("결제 상품명").type(SimpleType.STRING),
            fieldWithPath("payments[].amount").description("결제 금액").type(SimpleType.NUMBER),
            fieldWithPath("payments[].currency").description("통화 코드 (KRW)").type(SimpleType.STRING),
            fieldWithPath("payments[].status").description("결제 상태 (PENDING, PAID, FAILED, CANCELED)").type(SimpleType.STRING),
            fieldWithPath("payments[].paidAt").description("결제 완료 시각").type(SimpleType.STRING).optional(),
            fieldWithPath("pagination").description("페이지네이션 정보").type(JsonFieldType.OBJECT),
            fieldWithPath("pagination.page").description("현재 페이지 (1-based)").type(SimpleType.NUMBER),
            fieldWithPath("pagination.size").description("페이지 크기").type(SimpleType.NUMBER),
            fieldWithPath("pagination.totalElements").description("전체 항목 수").type(SimpleType.NUMBER),
            fieldWithPath("pagination.totalPages").description("전체 페이지 수").type(SimpleType.NUMBER),
            fieldWithPath("pagination.isFirst").description("첫 페이지 여부").type(SimpleType.BOOLEAN),
            fieldWithPath("pagination.isLast").description("마지막 페이지 여부").type(SimpleType.BOOLEAN)
    );

    private static final List<FieldDescriptor> DETAIL_RESPONSE_FIELDS = List.of(
            fieldWithPath("id").description("결제 ID").type(SimpleType.STRING),
            fieldWithPath("amount").description("결제 금액").type(SimpleType.NUMBER),
            fieldWithPath("currency").description("통화 코드 (KRW)").type(SimpleType.STRING),
            fieldWithPath("status").description("결제 상태").type(SimpleType.STRING),
            fieldWithPath("paymentMethod").description("결제 수단 유형 (CARD 등)").type(SimpleType.STRING).optional(),
            fieldWithPath("paidAt").description("결제 완료 시각").type(SimpleType.STRING).optional(),
            fieldWithPath("product").description("결제 상품 정보").type(JsonFieldType.OBJECT),
            fieldWithPath("product.name").description("상품명").type(SimpleType.STRING),
            fieldWithPath("product.price").description("상품 기본 가격").type(SimpleType.NUMBER),
            fieldWithPath("product.billingCycle").description("결제 주기").type(SimpleType.STRING)
    );

    private static final List<FieldDescriptor> INVOICE_RESPONSE_FIELDS = List.of(
            fieldWithPath("id").description("청구서(Invoice) ID").type(SimpleType.STRING),
            fieldWithPath("amount").description("청구 금액").type(SimpleType.NUMBER),
            fieldWithPath("currency").description("통화 코드 (KRW)").type(SimpleType.STRING),
            fieldWithPath("status").description("청구서 상태 (PENDING, PAID, FAILED)").type(SimpleType.STRING),
            fieldWithPath("startedAt").description("이용 기간 시작 시각").type(SimpleType.STRING),
            fieldWithPath("endedAt").description("이용 기간 종료 시각").type(SimpleType.STRING),
            fieldWithPath("paidAt").description("결제 완료 시각").type(SimpleType.STRING).optional()
    );

    public static ResourceSnippetParameters listSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Payment")
                .summary("결제 내역 목록 조회")
                .description("로그인 사용자의 결제 내역을 페이지네이션으로 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .queryParameters(LIST_QUERY_PARAMETERS)
                .responseFields(ApiResponseDocs.withDataFields(LIST_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters detailSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Payment")
                .summary("결제 상세 조회")
                .description("단일 결제 건의 상세 정보를 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .pathParameters(parameterWithName("paymentId").description("조회할 결제 ID"))
                .responseFields(ApiResponseDocs.withDataFields(DETAIL_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters detailNotFoundError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Payment")
                .pathParameters(parameterWithName("paymentId").description("조회할 결제 ID"))
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }

    public static ResourceSnippetParameters invoiceSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Payment")
                .summary("결제 청구서(영수증) 상세 조회")
                .description("결제 건에 연결된 청구서(이용 기간 및 청구 금액) 상세 정보를 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .pathParameters(parameterWithName("paymentId").description("조회할 결제 ID"))
                .responseFields(ApiResponseDocs.withDataFields(INVOICE_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters invoiceNotFoundError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Payment")
                .pathParameters(parameterWithName("paymentId").description("조회할 결제 ID"))
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }
}
