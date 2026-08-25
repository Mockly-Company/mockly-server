# 구독 연체·이용 정지 API 변경 안내

## 1. 변경 개요

유료 구독 갱신에 실패한 경우를 표시하기 위해 구독 상태에 `PAST_DUE`, `UNPAID`가 추가됩니다.

| 상태 | 의미 | 프론트에서 보여줄 상태 |
| --- | --- | --- |
| `ACTIVE` | 정상 이용 중 | 기존 화면 유지 |
| `PAST_DUE` | 결제 실패 후 7일 유예기간 | 기존 기능을 유지하고 결제 실패·유예 종료일 안내 |
| `UNPAID` | 유예기간 종료 후 미납 | interview 이용 정지 화면 표시 |

Free 사용자는 결제 대상이 아니므로 항상 `ACTIVE`입니다.

이번 변경에서 프론트가 처리할 핵심은 다음 세 가지입니다.

1. 구독 상태와 유예 종료일을 화면에 반영합니다.
2. `SUBSCRIPTION_UNPAID` 오류를 이용 정지 화면으로 연결합니다.
3. UNPAID 사용자를 Free 사용자로 잘못 표시하지 않습니다.

## 2. API 변경 사항

### 2.1 `GET /api/subscriptions`

응답의 `status`에 `PAST_DUE`, `UNPAID`가 추가됩니다.

다음 필드가 추가됩니다.

| 필드 | 설명 | 프론트 처리 |
| --- | --- | --- |
| `pastDueAt` | 최초 결제 실패 시각 | 필요할 때 결제 실패 시각 표시 |
| `gracePeriodEndsAt` | 유료 기능을 이용할 수 있는 마지막 시각 | PAST_DUE 안내 문구의 기준으로 사용 |

두 필드는 정상 구독에서는 `null`일 수 있습니다. `nextBillingDate`, `nextBillingAmount`도 PAST_DUE·UNPAID에서는 `null`일 수 있으므로 nullable로 처리합니다.

```json
{
  "id": 12,
  "status": "PAST_DUE",
  "startedAt": "2026-08-01T03:00:00",
  "currentPeriodStart": "2026-08-01T03:00:00",
  "currentPeriodEnd": "2026-09-01T03:00:00",
  "pastDueAt": "2026-09-01T03:00:00Z",
  "gracePeriodEndsAt": "2026-09-08T03:00:00Z",
  "nextBillingDate": null,
  "nextBillingAmount": null,
  "planSnapshot": {
    "id": 2,
    "name": "Basic",
    "price": 5900,
    "billingCycle": "MONTHLY"
  }
}
```

PAST_DUE에서는 기존 기능을 계속 사용할 수 있습니다. `gracePeriodEndsAt`을 기준으로 결제 실패 안내를 표시합니다.

UNPAID에서는 `planSnapshot`이 남아 있어도 이용 권한이 있는 유료 플랜으로 표시하지 않습니다.

### 2.2 `/api/interviews/**`

UNPAID 사용자 또는 유예기간이 끝난 PAST_DUE 사용자가 interview API를 호출하면 다음 오류가 반환됩니다.

| HTTP 상태 | `error` | 프론트 처리 |
| --- | --- | --- |
| `402` | `SUBSCRIPTION_UNPAID` | interview 화면을 닫고 이용 정지 안내 표시 |

```json
{
  "success": false,
  "data": null,
  "error": "SUBSCRIPTION_UNPAID",
  "message": "결제 문제로 서비스 이용이 정지되었습니다."
}
```

다음 API가 모두 대상입니다.

- 쿼터 조회
- 면접 생성
- 면접 목록·상세 조회
- 질문 SSE
- 답변 제출
- 피드백 SSE·조회·재시도
- 면접 중단

### 2.3 `GET /api/subscription-products`

UNPAID 사용자는 모든 `plans[].isActive`가 `false`입니다.

활성 플랜이 없다는 이유로 사용자를 Free로 대체하지 않습니다. 현재 상태는 `/api/subscriptions`의 `status`로 판단합니다.

## 3. 프론트 수정 대상

### 구독 상태와 타입

- 상태 enum 또는 union에 `PAST_DUE`, `UNPAID`를 추가합니다.
- `pastDueAt`, `gracePeriodEndsAt`을 nullable 시각으로 추가합니다.
- `nextBillingDate`, `nextBillingAmount`를 nullable로 변경합니다.
- Free 구독에 `id: null`을 합성하지 않습니다.

### 구독 화면

- PAST_DUE이면 기존 기능을 막지 않고 결제 실패와 유예 종료일을 안내합니다.
- 유예 종료일은 `gracePeriodEndsAt`을 사용합니다.
- UNPAID이면 유료 플랜 화면이 아니라 이용 정지 화면을 표시합니다.
- UNPAID 상태에서 활성 플랜이 없다고 Free 화면으로 자동 전환하지 않습니다.

### Interview 화면과 공통 오류 처리

- 앱 시작 시 구독 상태를 확인합니다.
- API 호출 중에도 `402 + SUBSCRIPTION_UNPAID`를 공통으로 처리합니다.
- 402를 네트워크 오류나 일반 서버 오류로 표시하지 않습니다.
- 목록·상세·피드백도 차단 대상이므로 예외적으로 열지 않습니다.
- 질문·피드백 SSE 연결에서 402가 발생해도 같은 이용 정지 화면으로 이동합니다.
- 이용 정지 화면에서 아직 존재하지 않는 결제 복구 API를 호출하지 않습니다.

## 4. 호환성 및 확인 기준

- `ACTIVE` 사용자의 기존 화면과 이용 흐름은 유지됩니다.
- PAST_DUE 사용자는 유예기간 동안 기존 interview 기능을 이용할 수 있습니다.
- UNPAID 사용자의 모든 interview 요청이 동일한 이용 정지 흐름으로 처리됩니다.
- nullable 결제 예정 필드 때문에 구독 화면이 깨지지 않습니다.
- UNPAID 사용자가 Free 사용자로 잘못 표시되지 않습니다.
- 결제 복구 API가 추가되기 전에는 고객센터 안내 등 실제로 동작하는 CTA만 제공합니다.
- 최종 필드명과 오류 응답은 백엔드 REST Docs/OpenAPI를 기준으로 확인합니다.
