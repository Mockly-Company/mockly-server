# Subscription Product·구독 조회 API 변경 안내

## 1. 변경 개요

프론트에서 확인할 변경은 현재 구독 조회의 상태·오류 계약과 상품 정책 필드 추가입니다. 결제 checkout, 플랜 변경, 연간 결제 UI는 이번 범위에 포함하지 않습니다.

## 2. API 변경 사항

### GET `/api/subscription-products`

상품에 아래 정책 필드가 추가됩니다.

```json
{
  "products": [
    {
      "id": 1,
      "name": "Free",
      "description": "무료 플랜",
      "planTier": "FREE",
      "maxQuestions": 3,
      "weeklyInterviewLimit": 1,
      "weeklyImprovementPracticeLimit": 0,
      "features": ["면접 연습 주 1회"],
      "plans": [
        {
          "id": 1,
          "price": 0,
          "currency": "KRW",
          "billingCycle": "MONTHLY",
          "isActive": false
        }
      ]
    }
  ]
}
```

| 필드 | 용도 | 프론트 반영 필요성 |
| --- | --- | --- |
| `planTier` | 이름과 무관하게 FREE/BASIC/PRO를 구분 | 등급별 UI 분기가 있을 때 사용 |
| `maxQuestions` | 해당 상품으로 한 면접에서 선택할 수 있는 최대 질문 수 | 질문 수 선택 UI가 있다면 사용 |
| `weeklyInterviewLimit` | 주간 면접 제공 횟수 | 상품 비교 화면에서 구조화된 숫자가 필요할 때 사용 |
| `weeklyImprovementPracticeLimit` | 주간 개선 연습 제공 횟수 | 상품 비교 화면에서 구조화된 숫자가 필요할 때 사용 |

신규 필드는 모두 추가 필드이므로 기존 상품 화면이 반드시 수정되어야 하는 것은 아닙니다. 숫자를 화면 로직에 사용해야 할 때 `features` 문구를 파싱하거나 프론트에 같은 정책값을 하드코딩하지 않도록 제공한 값입니다.

### GET `/api/subscriptions`

성공 응답에는 ACTIVE 구독을 우선 반환하고, ACTIVE가 없으면 PAST_DUE 구독을 반환합니다. 따라서 프론트는 `status`로 두 상태를 모두 받을 수 있습니다.

Free 사용자도 실제 구독 행을 가지므로 성공 응답의 `id`는 항상 실제 구독 ID입니다. Free를 표시하기 위해 프론트가 `id: null`인 임시 구독 객체를 만들 필요가 없습니다.

현재 구독이 없을 때의 계약은 변경됩니다.

- 이전: `200 OK`, `data: null`
- 이후: `404 Not Found`, `error: "RESOURCE_NOT_FOUND"`

이 오류는 정상적인 Free 상태가 아니라 가입 데이터 정합성 문제를 뜻합니다. 클라이언트가 임의의 Free 구독 객체를 만들어 대체하지 않아야 합니다.

## 3. 프론트 수정 대상

### 이번 배포 필수

1. `/api/subscriptions` 응답 상태에 `PAST_DUE`가 올 수 있도록 처리합니다.
2. 구독 성공 응답의 `id`를 실제 구독 ID로 사용합니다. Free에 `null` ID를 합성하지 않습니다.
3. `/api/subscriptions`의 `RESOURCE_NOT_FOUND`를 일반 Free 상태로 처리하지 말고 오류 추적 대상으로 구분합니다.

### 필요할 때 반영

- 질문 수 선택 UI는 `maxQuestions`를 사용합니다.
- 상품 정책을 숫자로 비교하거나 표시해야 한다면 주간 한도 필드를 사용합니다.
- 등급별 UI 분기는 상품명 대신 `planTier`를 사용합니다.

## 4. 호환성 및 확인 기준

- 상품 정책 필드는 additive change이므로 사용하지 않는 프론트에는 영향이 없습니다.
- `/api/subscriptions`의 구독 미존재 응답은 breaking change입니다.
- 최종 필드명과 오류 계약은 Spring REST Docs에서 생성되는 OpenAPI 문서를 기준으로 확인합니다.
