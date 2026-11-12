package app.mockly.domain.product.controller.docs;

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

public class SubscriptionDocs {

    private static final List<HeaderDescriptorWithType> REQUEST_HEADERS = List.of(
            headerWithName("Authorization").description("Bearer {accessToken}")
    );

    private static final List<FieldDescriptor> CREATE_REQUEST_FIELDS = List.of(
            fieldWithPath("planId").description("구독할 플랜 ID").type(JsonFieldType.NUMBER),
            fieldWithPath("expectedPrice").description("예상 결제 금액").type(JsonFieldType.NUMBER),
            fieldWithPath("paymentMethodId").description("결제 수단 ID").type(JsonFieldType.NUMBER)
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
            fieldWithPath("status").description("구독 상태 (ACTIVE, PAST_DUE, UNPAID)").type(SimpleType.STRING),
            fieldWithPath("cancelAtPeriodEnd").description("기간 만료 시 해지 예약 여부").type(SimpleType.BOOLEAN),
            fieldWithPath("startedAt").description("구독 시작 시각").type(SimpleType.STRING),
            fieldWithPath("currentPeriodStart").description("현재 결제 주기 시작").type(SimpleType.STRING).optional(),
            fieldWithPath("currentPeriodEnd").description("현재 결제 주기 종료").type(SimpleType.STRING).optional(),
            fieldWithPath("pastDueAt").description("최초 갱신 결제 실패 시각 (ACTIVE이면 null)").type(SimpleType.STRING).optional(),
            fieldWithPath("gracePeriodEndsAt").description("연체 유예 종료 시각 (ACTIVE이면 null)").type(SimpleType.STRING).optional(),
            fieldWithPath("nextBillingDate").description("다음 결제일 (해지 예약, 무료, 연체 시 null)").type(SimpleType.STRING).optional(),
            fieldWithPath("nextBillingAmount").description("다음 결제 금액 (해지 예약, 무료, 연체 시 null)").type(SimpleType.NUMBER).optional(),
            fieldWithPath("planSnapshot").description("플랜 스냅샷").type(JsonFieldType.OBJECT),
            fieldWithPath("planSnapshot.id").description("플랜 ID").type(JsonFieldType.NUMBER),
            fieldWithPath("planSnapshot.name").description("상품명").type(SimpleType.STRING),
            fieldWithPath("planSnapshot.price").description("가격").type(SimpleType.NUMBER),
            fieldWithPath("planSnapshot.billingCycle").description("결제 주기").type(SimpleType.STRING),
            fieldWithPath("scheduledPlanName").description("다음 주기 적용 예정 다운그레이드 상품명").type(SimpleType.STRING).optional(),
            fieldWithPath("cardBrand").description("연결 또는 기본 결제 카드 브랜드").type(SimpleType.STRING).optional(),
            fieldWithPath("cardNumber").description("연결 또는 기본 결제 카드 마스킹 번호").type(SimpleType.STRING).optional()
    );

    private static final List<FieldDescriptor> CANCEL_REQUEST_FIELDS = List.of(
            fieldWithPath("immediate").description("즉시 해지 여부 (기본값 false: 기간 만료 시 해지 예약)").type(SimpleType.BOOLEAN).optional()
    );

    private static final List<FieldDescriptor> CANCEL_RESPONSE_FIELDS = List.of(
            fieldWithPath("id").description("구독 ID").type(SimpleType.NUMBER),
            fieldWithPath("status").description("구독 상태 (예약 해지 시 ACTIVE, 즉시/연체 해지 시 CANCELED)").type(SimpleType.STRING),
            fieldWithPath("cancelAtPeriodEnd").description("기간 만료 시 해지 예약 여부").type(SimpleType.BOOLEAN),
            fieldWithPath("canceledAt").description("해지 요청 시각").type(SimpleType.STRING),
            fieldWithPath("availableUntil").description("서비스 이용 가능 기한").type(SimpleType.STRING)
    );

    private static final List<FieldDescriptor> UPDATE_PAYMENT_METHOD_REQUEST_FIELDS = List.of(
            fieldWithPath("paymentMethodId").description("변경할 결제 수단 ID").type(JsonFieldType.NUMBER)
    );

    private static final List<ParameterDescriptorWithType> PLAN_CHANGE_QUERY_PARAMETERS = List.of(
            parameterWithName("targetPlanId").description("변경할 대상 유료 플랜 ID (하위 호환 별칭 targetPlan 지원)").type(SimpleType.INTEGER).optional(),
            parameterWithName("targetPlan").description("targetPlanId 하위 호환 쿼리 파라미터").type(SimpleType.INTEGER).optional()
    );

    private static final List<FieldDescriptor> PLAN_CHANGE_PREVIEW_RESPONSE_FIELDS = List.of(
            fieldWithPath("currentPlan").description("현재 이용 중인 플랜 정보").type(JsonFieldType.OBJECT),
            fieldWithPath("currentPlan.id").description("현재 플랜 ID").type(SimpleType.NUMBER),
            fieldWithPath("currentPlan.name").description("현재 상품명").type(SimpleType.STRING),
            fieldWithPath("currentPlan.price").description("현재 플랜 가격").type(SimpleType.NUMBER),
            fieldWithPath("currentPlan.billingCycle").description("현재 결제 주기").type(SimpleType.STRING),
            fieldWithPath("currentPlan.features").description("현재 플랜 혜택 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("targetPlan").description("변경 대상 플랜 정보").type(JsonFieldType.OBJECT),
            fieldWithPath("targetPlan.id").description("대상 플랜 ID").type(SimpleType.NUMBER),
            fieldWithPath("targetPlan.name").description("대상 상품명").type(SimpleType.STRING),
            fieldWithPath("targetPlan.price").description("대상 플랜 가격").type(SimpleType.NUMBER),
            fieldWithPath("targetPlan.billingCycle").description("대상 결제 주기").type(SimpleType.STRING),
            fieldWithPath("targetPlan.features").description("대상 플랜 혜택 목록").type(JsonFieldType.ARRAY),
            fieldWithPath("immediateAmount").description("즉시 결제 차액 (업그레이드 시 양수, 다운그레이드 시 0)").type(SimpleType.NUMBER),
            fieldWithPath("nextBillingAmount").description("다음 결제 주기 청구 금액").type(SimpleType.NUMBER),
            fieldWithPath("nextBillingDate").description("다음 정기 결제 예정 시각").type(SimpleType.STRING)
    );

    private static final List<FieldDescriptor> PLAN_CHANGE_REQUEST_FIELDS = List.of(
            fieldWithPath("targetPlanId").description("변경할 대상 유료 플랜 ID").type(JsonFieldType.NUMBER),
            fieldWithPath("confirmedAmount").description("사용자가 확인한 즉시 정산 금액").type(JsonFieldType.NUMBER)
    );

    private static final List<FieldDescriptor> PLAN_CHANGE_RESPONSE_FIELDS = List.of(
            fieldWithPath("chargedAmount").description("즉시 청구된 정산 금액").type(SimpleType.NUMBER),
            fieldWithPath("currency").description("통화 코드 (KRW)").type(SimpleType.STRING),
            fieldWithPath("requestedAt").description("플랜 변경 요청 시각").type(SimpleType.STRING),
            fieldWithPath("effectiveAt").description("변경 플랜 적용 시각 (업그레이드는 즉시, 다운그레이드는 주기 종료일)").type(SimpleType.STRING),
            fieldWithPath("subscription").description("변경 후 구독 요약").type(JsonFieldType.OBJECT),
            fieldWithPath("subscription.id").description("구독 ID").type(SimpleType.NUMBER),
            fieldWithPath("subscription.status").description("구독 상태 (ACTIVE)").type(SimpleType.STRING),
            fieldWithPath("subscription.startedAt").description("구독 시작 시각").type(SimpleType.STRING),
            fieldWithPath("subscription.endedAt").description("현재 주기 서비스 이용 종료 시각").type(SimpleType.STRING),
            fieldWithPath("subscription.nextBillingDate").description("다음 결제일").type(SimpleType.STRING),
            fieldWithPath("subscription.nextBillingAmount").description("다음 결제 예정 금액").type(SimpleType.NUMBER),
            fieldWithPath("subscription.scheduledPlanName").description("다운그레이드 예약 시 다음 주기 적용 상품명").type(SimpleType.STRING).optional(),
            fieldWithPath("subscription.planSnapshot").description("현재 활성 플랜 스냅샷").type(JsonFieldType.OBJECT),
            fieldWithPath("subscription.planSnapshot.id").description("플랜 ID").type(SimpleType.NUMBER),
            fieldWithPath("subscription.planSnapshot.name").description("상품명").type(SimpleType.STRING),
            fieldWithPath("subscription.planSnapshot.price").description("플랜 가격").type(SimpleType.NUMBER),
            fieldWithPath("subscription.planSnapshot.billingCycle").description("결제 주기").type(SimpleType.STRING),
            fieldWithPath("subscription.planSnapshot.features").description("플랜 혜택 목록").type(JsonFieldType.ARRAY)
    );

    public static ResourceSnippetParameters createSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
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
                .privateResource(true)
                .tag("Subscription")
                .responseFields(ApiResponseDocs.errorResponse("BAD_REQUEST"))
                .build();
    }

    public static ResourceSnippetParameters createPlanNotFoundError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }

    public static ResourceSnippetParameters getSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .summary("내 구독 조회")
                .description("현재 ACTIVE, PAST_DUE 또는 UNPAID 구독 정보를 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .responseFields(ApiResponseDocs.withDataFields(GET_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters getNotFound() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }

    public static ResourceSnippetParameters cancelSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .summary("구독 해지")
                .description("유료 구독을 해지합니다. 기본값(immediate=false)은 현재 결제 주기 종료 시점 해지를 예약하며, 즉시 해지(immediate=true) 또는 연체(PAST_DUE, UNPAID) 상태 해지 시 즉시 CANCELED 처리 후 Free 플랜을 배정합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .pathParameters(parameterWithName("id").description("해지할 구독 ID"))
                .requestFields(CANCEL_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(CANCEL_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters cancelForbiddenError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .pathParameters(parameterWithName("id").description("해지할 구독 ID"))
                .responseFields(ApiResponseDocs.errorResponse("FORBIDDEN"))
                .build();
    }

    public static ResourceSnippetParameters updatePaymentMethodSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .summary("구독 결제 수단 변경 및 연체 복구")
                .description("ACTIVE, PAST_DUE, UNPAID 구독의 결제 수단을 변경합니다. 연체(PAST_DUE, UNPAID) 상태인 경우 새 빌링키로 즉시 결제를 승인하고 성공 시 ACTIVE로 복구합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .pathParameters(parameterWithName("id").description("구독 ID"))
                .requestFields(UPDATE_PAYMENT_METHOD_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.noContentFields())
                .build();
    }

    public static ResourceSnippetParameters updatePaymentMethodForbiddenError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .pathParameters(parameterWithName("id").description("구독 ID"))
                .responseFields(ApiResponseDocs.errorResponse("FORBIDDEN"))
                .build();
    }

    public static ResourceSnippetParameters updatePaymentMethodNotFoundError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .pathParameters(parameterWithName("id").description("구독 ID"))
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }

    public static ResourceSnippetParameters planChangePreviewSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .summary("플랜 변경 예상 금액 조회")
                .description("현재 유료 구독에서 대상 유료 플랜으로 변경할 때의 잔여 일수 비례 즉시 결제 차액과 다음 결제 예정 정보를 조회합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .queryParameters(PLAN_CHANGE_QUERY_PARAMETERS)
                .responseFields(ApiResponseDocs.withDataFields(PLAN_CHANGE_PREVIEW_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters planChangePreviewBadRequestError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .responseFields(ApiResponseDocs.errorResponse("BAD_REQUEST"))
                .build();
    }

    public static ResourceSnippetParameters planChangePreviewNotFoundError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }

    public static ResourceSnippetParameters planChangeSuccess() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .summary("구독 플랜 변경 실행")
                .description("유료 플랜 간 업그레이드(차액 즉시 결제 후 즉시 전환) 또는 다운그레이드(다음 결제 주기 전환 예약)를 수행합니다.")
                .requestHeaders(REQUEST_HEADERS)
                .requestFields(PLAN_CHANGE_REQUEST_FIELDS)
                .responseFields(ApiResponseDocs.withDataFields(PLAN_CHANGE_RESPONSE_FIELDS))
                .build();
    }

    public static ResourceSnippetParameters planChangeBadRequestError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .responseFields(ApiResponseDocs.errorResponse("BAD_REQUEST"))
                .build();
    }

    public static ResourceSnippetParameters planChangeNotFoundError() {
        return ResourceSnippetParameters.builder()
                .privateResource(true)
                .tag("Subscription")
                .responseFields(ApiResponseDocs.errorResponse("RESOURCE_NOT_FOUND"))
                .build();
    }
}
