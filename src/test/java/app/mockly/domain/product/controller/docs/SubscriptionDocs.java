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

public class SubscriptionDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<FieldDescriptor> CREATE_REQUEST_FIELDS = List.of(
            fieldWithPath("planId").description("구독할 플랜 ID").type(JsonFieldType.NUMBER)
    );

    private static final List<FieldDescriptor> CREATE_RESPONSE_FIELDS = List.of(
            fieldWithPath("id").description("구독 ID").type(SimpleType.NUMBER),
            fieldWithPath("status").description("구독 상태 (ACTIVE)").type(SimpleType.STRING),
            fieldWithPath("startedAt").description("구독 시작 시각").type(SimpleType.STRING),
            fieldWithPath("endedAt").description("서비스 이용 종료일").type(SimpleType.STRING),
            fieldWithPath("nextBillingDate").description("다음 결제일").type(SimpleType.STRING),
            fieldWithPath("nextBillingAmount").description("다음 결제 금액").type(SimpleType.NUMBER),
            fieldWithPath("planSnapshot").description("플랜 스냅샷").type(JsonFieldType.OBJECT),
            fieldWithPath("planSnapshot.id").description("플랜 ID").type(JsonFieldType.NUMBER),
            fieldWithPath("planSnapshot.name").description("상품명").type(SimpleType.STRING),
            fieldWithPath("planSnapshot.price").description("가격").type(SimpleType.NUMBER),
            fieldWithPath("planSnapshot.billingCycle").description("결제 주기").type(SimpleType.STRING)
    );

    private static final List<FieldDescriptor> GET_RESPONSE_FIELDS = List.of(
            fieldWithPath("id").description("구독 ID").type(SimpleType.NUMBER),
            fieldWithPath("status").description("구독 상태").type(SimpleType.STRING),
            fieldWithPath("startedAt").description("구독 시작 시각").type(SimpleType.STRING),
            fieldWithPath("currentPeriodStart").description("현재 결제 주기 시작").type(SimpleType.STRING),
            fieldWithPath("currentPeriodEnd").description("현재 결제 주기 종료").type(SimpleType.STRING),
            fieldWithPath("nextBillingDate").description("다음 결제일").type(SimpleType.STRING),
            fieldWithPath("nextBillingAmount").description("다음 결제 금액").type(SimpleType.NUMBER),
            fieldWithPath("planSnapshot").description("플랜 스냅샷").type(JsonFieldType.OBJECT),
            fieldWithPath("planSnapshot.id").description("플랜 ID").type(JsonFieldType.NUMBER),
            fieldWithPath("planSnapshot.name").description("상품명").type(SimpleType.STRING),
            fieldWithPath("planSnapshot.price").description("가격").type(SimpleType.NUMBER),
            fieldWithPath("planSnapshot.billingCycle").description("결제 주기").type(SimpleType.STRING)
    );

    private static final List<FieldDescriptor> CANCEL_RESPONSE_FIELDS = List.of(
            fieldWithPath("id").description("구독 ID").type(SimpleType.NUMBER),
            fieldWithPath("status").description("구독 상태 (CANCELED)").type(SimpleType.STRING),
            fieldWithPath("canceledAt").description("해지 시각").type(SimpleType.STRING),
            fieldWithPath("availableUntil").description("서비스 이용 가능 기한").type(SimpleType.STRING)
    );

    public static ResourceSnippetParameters createSuccess() {
        return ResourceSnippetParameters.builder()
                .tag("Subscription")
                .summary("구독 생성")
                .description("유료 플랜 구독을 생성합니다. 무료 플랜은 회원가입 시 자동 부여됩니다.")
                .requestHeaders(REQUEST_HEADERS)
                .requestFields(CREATE_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(CREATE_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters createFreePlanError() {
        return ResourceSnippetParameters.builder()
                .tag("Subscription")
                .summary("구독 생성 - 무료 플랜 에러")
                .description("무료 플랜은 직접 구독할 수 없습니다.")
                .responseFields(ApiResponseDocs.errorResponse("BAD_REQUEST"))
                .build();
    }

    public static ResourceSnippetParameters createPlanNotFoundError() {
        return ResourceSnippetParameters.builder()
                .tag("Subscription")
                .summary("구독 생성 - 플랜 없음 에러")
                .description("존재하지 않는 플랜입니다.")
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }

    public static ResourceSnippetParameters getSuccess() {
        return ResourceSnippetParameters.builder()
                .tag("Subscription")
                .summary("내 구독 조회")
                .description("현재 활성화된 구독 정보를 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(GET_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters getEmpty() {
        return ResourceSnippetParameters.builder()
                .tag("Subscription")
                .summary("내 구독 조회 - 구독 없음")
                .description("활성화된 구독이 없는 경우 data가 null로 반환됩니다.")
                .requestHeaders(REQUEST_HEADERS)
                .responseFields(ApiResponseDocs.noContentFields())
                .build();
    }

    public static ResourceSnippetParameters cancelSuccess() {
        return ResourceSnippetParameters.builder()
                .tag("Subscription")
                .summary("구독 해지")
                .description("구독을 해지합니다. 현재 결제 주기 종료까지 서비스 이용 가능합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(CANCEL_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters cancelForbiddenError() {
        return ResourceSnippetParameters.builder()
                .tag("Subscription")
                .summary("구독 해지 - 권한 없음")
                .description("본인의 구독만 해지할 수 있습니다.")
                .responseFields(ApiResponseDocs.errorResponse("FORBIDDEN"))
                .build();
    }
}
