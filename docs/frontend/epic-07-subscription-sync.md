# 구독 해지 예약·연체 복구·플랜 변경 API 변경 안내 (Epic #7)

## 1. 변경 개요

PortOne v2 단일 결제 경로를 기준으로 구독 해지 예약(유예 후 Free 복귀), 연체(`PAST_DUE` / `UNPAID`) 상태 복구, 유료 플랜 간 변경(`Basic` ↔ `Pro`) API 계약을 동기화합니다.

- **변경 목적**:
  - 유료 구독 해지 시 남은 결제 주기까지 유료 권한을 유지하고 만료 시 `Free` 플랜으로 자동 전환되도록 개선합니다.
  - 즉시 해지 또는 연체(`PAST_DUE`, `UNPAID`) 해지 시 동일 트랜잭션에서 `Free` 플랜을 자동 배정하여 `404 RESOURCE_NOT_FOUND` 고아 상태를 방지합니다.
  - 연체 상태에서 새 결제 수단으로 교체하면 즉시 미납 결제를 승인하여 `ACTIVE` 상태로 복구합니다.
  - 모바일 클라이언트의 플랜 변경 미리보기·실행(`GET / POST /api/subscriptions/plan-change`) 계약을 완성합니다.
- **범위**: `/api/subscriptions`, `/api/subscriptions/{id}`, `/api/subscriptions/{id}/payment-method`, `/api/subscriptions/plan-change`, `Free` 상품 `features` 문구 정합성(`"기본 면접 히스토리 조회"`).
- **제외 항목**: PayApp 레거시 체크아웃 엔드포인트는 도입하지 않으며 PortOne v2 빌링키 단일 경로만 지원합니다.

## 2. API 변경 사항

### 2.1 `GET /api/subscriptions`

응답에 `cancelAtPeriodEnd`가 추가되며, 조건부 필드(`scheduledPlanName`, `cardBrand`, `cardNumber`)가 존재하는 경우 포함됩니다.

| 필드 | 타입 | 변경 사항 | 설명 |
| --- | --- | --- | --- |
| `cancelAtPeriodEnd` | `boolean` | 신규 | 현재 결제 주기 종료 시 해지 예약 여부 (`true`이면 `nextBillingDate`, `nextBillingAmount`는 `null`) |
| `scheduledPlanName` | `string` (optional) | 신규 | 다운그레이드 예약 시 다음 주기 적용 예정 상품명 (`"Basic"` 등, 미예약 시 생략) |
| `cardBrand` | `string` (optional) | 신규 | 연결 결제수단 또는 기본 활성 결제수단 카드 브랜드 (없으면 생략) |
| `cardNumber` | `string` (optional) | 신규 | 마스킹된 카드 번호 (예: `"1234****5678"`, 없으면 생략) |

```json
{
  "success": true,
  "data": {
    "id": 12,
    "status": "ACTIVE",
    "cancelAtPeriodEnd": false,
    "startedAt": "2026-11-01T09:00:00",
    "currentPeriodStart": "2026-11-01T09:00:00",
    "currentPeriodEnd": "2026-12-01T09:00:00",
    "pastDueAt": null,
    "gracePeriodEndsAt": null,
    "nextBillingDate": "2026-12-01T09:00:00",
    "nextBillingAmount": 5900,
    "planSnapshot": {
      "id": 2,
      "name": "Basic",
      "price": 5900,
      "billingCycle": "MONTHLY"
    },
    "cardBrand": "VISA",
    "cardNumber": "1234****5678"
  },
  "error": null,
  "message": null,
  "timestamp": 1762900000000
}
```

### 2.2 `DELETE /api/subscriptions/{id}`

선택적 요청 바디 `{ "immediate": false }`를 지원합니다(바디 생략 시 `immediate = false`).

- `ACTIVE` 유료 구독 + `immediate: false`: PortOne 예약 스케줄을 취소하고 `cancelAtPeriodEnd = true`로 설정하며 `currentPeriodEnd`까지 `status = "ACTIVE"`를 유지합니다. 만료 시 스케줄러(`CanceledPeriodEndSubscriptionJob`)가 자동으로 `CANCELED` 마감 및 `Free` 플랜을 배정합니다.
- `immediate: true` 또는 `PAST_DUE` / `UNPAID` 상태에서 호출: 즉시 `status = "CANCELED"`로 마감하고 동일 트랜잭션에서 `Free` 활성 구독을 배정합니다.

```json
{
  "success": true,
  "data": {
    "id": 12,
    "status": "ACTIVE",
    "cancelAtPeriodEnd": true,
    "canceledAt": "2026-11-12T10:00:00",
    "availableUntil": "2026-12-01T09:00:00"
  },
  "error": null,
  "message": null,
  "timestamp": 1762900000000
}
```

### 2.3 `PUT /api/subscriptions/{id}/payment-method`

- 허용 상태가 기존 `ACTIVE` 전용에서 `ACTIVE`, `PAST_DUE`, `UNPAID`로 확대됩니다.
- `PAST_DUE` 또는 `UNPAID` 상태에서 호출하면 새 `paymentMethodId`의 빌링키로 즉시 결제를 승인 요청하고, 승인 성공 시 `status = "ACTIVE"`, `pastDueAt = null`, 새 이용 기간(`now ~ +1개월`) 및 다음 정기 결제 스케줄을 생성합니다.
- 결제 승인 실패 시 `500 INTERNAL_SERVER_ERROR`를 반환하며 기존 연체 상태가 유지됩니다.

### 2.4 `GET /api/subscriptions/plan-change` (신규)

- 쿼리 파라미터: `?targetPlanId={id}` (기존 클라이언트 호환을 위해 `?targetPlan={id}` 별칭도 동일하게 동작)
- 현재 유료 구독(`ACTIVE`) 기준 대상 유료 플랜 변경 시의 잔여 일수 비례 차액(`immediateAmount`)과 다음 결제 금액(`nextBillingAmount`), 다음 결제일(`nextBillingDate`)을 반환합니다.

```json
{
  "success": true,
  "data": {
    "currentPlan": {
      "id": 2,
      "name": "Basic",
      "price": 5900,
      "billingCycle": "MONTHLY",
      "features": ["AI 면접 연습 주 4회", "AI 상세 피드백 제공"]
    },
    "targetPlan": {
      "id": 3,
      "name": "Pro",
      "price": 9900,
      "billingCycle": "MONTHLY",
      "features": ["AI 면접 연습 주 10회", "개선 연습 주 4회"]
    },
    "immediateAmount": 4000,
    "nextBillingAmount": 9900,
    "nextBillingDate": "2026-12-01T09:00:00"
  },
  "error": null,
  "message": null,
  "timestamp": 1762900000000
}
```

### 2.5 `POST /api/subscriptions/plan-change` (신규)

- 요청 바디: `{ "targetPlanId": 3, "confirmedAmount": 4000 }`
- **업그레이드(`Basic → Pro`)**: `confirmedAmount`와 예상 `immediateAmount` 일치 검증 후 차액을 즉시 결제하고 `planSnapshot`을 즉시 상위 플랜으로 전환합니다 (`effectiveAt = requestedAt`).
- **다운그레이드(`Pro → Basic`)**: 즉시 결제 금액 `0`원으로 `scheduledPlanName = "Basic"`을 예약하며 현재 주기 종료일(`effectiveAt = currentPeriodEnd`)부터 하위 플랜으로 갱신됩니다.

## 3. 프론트 수정 대상

### 이번 배포 필수
- `@mockly/api/src/subscription/getUserSubscription.ts`: `cancelAtPeriodEnd` 불리언 및 `scheduledPlanName` 옵셔널 문자열을 도메인 타입에 매핑합니다.
- `@mockly/api/src/subscription/getExpectedSubscriptionAmount.ts`: 기존의 리터럴 하드코딩(`9900`, `14900`, `5000`) 및 `catch` 가짜 폴백을 제거하고 서버 실응답 스키마(`targetPlanId` 쿼리 전달)를 파싱합니다.
- 구독 관리 및 이용 정지(`SubscriptionSuspendedScreen`) 화면에서 연체 상태의 결제수단 교체 복구(`PUT /api/subscriptions/{id}/payment-method`) 및 Free 복귀 해지(`DELETE /api/subscriptions/{id}`) 동선을 연결합니다.

### 후속 API 대기
- 없음 (이번 PR에서 구독·결제 동기화 API가 모두 제공됩니다).

### 선택 개선 항목
- 다운그레이드 예약 상태(`scheduledPlanName != null`)일 때 구독 카드에 다음 결제일부터 변경될 플랜 안내 배지를 표시합니다.

## 4. 호환성 및 확인 기준

- **하위 호환성 유지**:
  - `cardBrand`, `cardNumber`, `scheduledPlanName`은 값이 없을 때 JSON 키 자체를 생략(`NON_NULL`)하므로 기존 클라이언트의 `z.string().optional()` 검증과 100% 호환됩니다.
  - `DELETE /api/subscriptions/{id}`는 요청 바디 없이 호출해도 `immediate = false`로 안전하게 동작합니다.
  - `GET /api/subscriptions/plan-change`는 `?targetPlanId=`와 기존 `?targetPlan=` 쿼리 파라미터를 모두 허용합니다.
- **확인 기준**: 최종 요청/응답 스키마 및 오류 코드는 `./gradlew test openapi3`로 생성된 Spring REST Docs 및 `openapi3.yaml`을 기준으로 확인합니다.
- **서버 운영·보안 참고 (빌링키 암호화 및 키 로테이션)**:
  - `BILLING_KEY_ENCRYPTION_SECRET` 환경변수는 필수이며 미설정 또는 빈 값일 경우 `BillingKeyCryptoInitializer`에서 기동 즉시 실패(fail-fast)합니다.
  - 신규/갱신 저장되는 `payment_method.billing_key` 및 `outbox_event.payload` 내 빌링키는 PBKDF2(`PBKDF2WithHmacSHA256`, 65,536회)로 파생한 256-bit AES-GCM(`enc:v1:` 접두사 + 12바이트 랜덤 IV)으로 암호화됩니다.
  - 무중단 마이그레이션을 위해 복호화 시 `enc:v1:` 접두사가 없는 기존 평문 행은 읽기를 허용하되, 재저장 배치(`PaymentMethod` 엔티티 조회 후 재저장)로 전량 `enc:v1:` 암호문으로 전환하며, 향후 키 교체 시 `enc:v2:` 버전 접두사를 추가해 이전 키 복호화 → 신규 키 재암호화 순서로 로테이션합니다.
