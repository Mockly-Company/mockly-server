# 면접 주간 쿼터 API 변경 안내

## 1. 변경 개요

면접 이용 한도가 일일 기준에서 **구독 시작일 기준 7일 주기**로 바뀝니다. 이용 시작일의 KST 날짜를 기준으로, 매 7일째 `00:00`에 새로운 이용기간이 시작됩니다.

기존 일일 쿼터는 사용자가 자정에 한도가 초기화되는 것으로 이해하게 만들고, 구독 이용기간과도 기준이 달랐습니다. 새 계약은 구독 권한과 쿼터 주기를 같은 기준으로 맞추기 위한 변경입니다.

이번 변경 범위는 면접 쿼터와 세션 응답입니다. 결제·플랜 변경 API와 개선 연습 생성·답변 API는 포함하지 않습니다.

## 2. API 변경 사항

### 2.1 `GET /api/interviews/quota`

기존 일일 쿼터 응답은 제거되고, 현재 주간 이용기간의 면접·개선 연습 사용량을 함께 반환합니다.

| 기존 필드 | 변경 후 필드 | 의미 |
| --- | --- | --- |
| `dailyLimit` | `interview.limit` | 현재 이용기간의 면접 한도 |
| `usedToday` | `interview.used` | 현재 이용기간의 면접 사용량 |
| `remaining` | `interview.remaining` | 현재 이용기간의 면접 잔여량 |
| `maxQuestionsPerSession` | `maxQuestions` | 세션당 선택 가능한 최대 질문 수 |
| 없음 | `periodStart` | 현재 이용기간 시작 시각(KST) |
| 없음 | `nextResetAt` | 다음 이용기간 시작 시각(KST) |
| 없음 | `improvementPractice` | 개선 연습의 한도·사용량·잔여량 |

```json
{
  "periodStart": "2026-08-17T00:00:00+09:00",
  "nextResetAt": "2026-08-24T00:00:00+09:00",
  "maxQuestions": 3,
  "interview": {
    "limit": 1,
    "used": 0,
    "remaining": 1
  },
  "improvementPractice": {
    "limit": 0,
    "used": 0,
    "remaining": 0
  }
}
```

`periodStart`와 `nextResetAt`은 KST 오프셋이 포함된 ISO-8601 시각입니다.

### 2.2 `POST /api/interviews`

요청 형식과 성공 응답은 변경되지 않습니다. 단, 서버가 아래 기준으로 생성 가능 여부를 판정합니다.

- 질문 수는 `maxQuestions`를 초과할 수 없습니다.
- 면접 한도는 현재 이용기간의 `interview.remaining` 기준입니다.

| 상황 | HTTP 상태 | `error` | 프론트 처리 |
| --- | --- | --- | --- |
| 면접 한도 소진 | `429` | `QUOTA_EXCEEDED` | 쿼터를 다시 조회하고 `nextResetAt` 기준 재이용 가능 시점 안내 |
| 선택한 질문 수가 허용 범위 초과 | `400` | `VALIDATION_ERROR` | 질문 수를 `maxQuestions` 이하로 조정 |

> `QUOTA_EXCEEDED`의 서버 기본 `message`에는 이전 일일 정책 문구가 남아 있을 수 있습니다. 화면 문구는 기본 메시지가 아니라 `error` 코드와 `nextResetAt`을 기준으로 구성합니다.

### 2.3 세션 목록·상세 응답

기존 필드는 유지되고, 아래 필드가 추가됩니다. 모두 값이 없을 수 있으므로 선택적으로 표시해야 합니다.

| 필드 | 의미 |
| --- | --- |
| `endedAt` | 면접 종료 또는 abandon 시각 |
| `durationSeconds` | 시작부터 종료까지의 면접 시간(초) |
| `overallScore` | 피드백 생성 후의 종합 점수 |
| `feedbackStatus` | `PENDING`, `GENERATING`, `COMPLETED`, `FAILED` 또는 `null` |

## 3. 프론트 수정 대상

### 3.1 이번 배포에 필요한 변경

| 위치 | 수정 내용 |
| --- | --- |
| `packages/api/src/interview/` | `getInterviewQuota` API 함수와 응답 DTO→클라이언트 타입 변환을 추가하고 barrel export에 등록 |
| `apps/app/src/configs/queryClient/QueryKeys.ts` | `queries.interview.quota()`를 추가 |
| `apps/app/src/features/interview/components/SessionSetup.tsx` | quota API로 질문 수·잔여 면접·다음 리셋일을 표시하고, 잔여 면접이 0이면 시작 CTA를 비활성화 |
| `apps/app/src/features/interview/hooks/useInterviewSession.ts` | 면접 생성 성공 또는 `QUOTA_EXCEEDED` 후 quota query를 무효화하고 최신 상태로 다시 조회 |

#### 면접 설정 화면

- 기존의 플랜 이름과 하드코딩된 질문 수 정책은 권한 판단에 사용하지 않습니다.
- 질문 수 UI는 현재 `3 / 5 / 10` 선택지 중 `maxQuestions` 이하만 노출합니다.
- `interview.remaining`이 `0`이면 시작 버튼을 막고, “현재 이용기간 한도를 모두 사용했어요. 다음 리셋일: …”처럼 안내합니다.
- 기존 “오늘 남은 면접”, “내일 다시 시도” 문구는 각각 “현재 이용기간 남은 면접”, `nextResetAt` 기반 안내로 바꿉니다.

#### 면접 생성 실패 처리

버튼 비활성화는 사용자 경험을 위한 사전 처리일 뿐, 최종 한도 판정은 서버가 합니다. 다른 기기에서 먼저 면접을 시작하는 등으로 `POST /api/interviews`가 `429`를 반환할 수 있습니다.

이 경우 일반 오류 화면을 노출하지 않고 다음 흐름으로 처리합니다.

1. `QUOTA_EXCEEDED`를 식별한다.
2. quota query를 다시 조회한다.
3. 최신 `nextResetAt`으로 재이용 가능 시점을 안내한다.
4. 면접 설정 화면을 유지하고 시작 CTA를 비활성화한다.

### 3.2 후속 API 구현 후 변경할 화면

아래 화면은 현재 월간 문구와 임시 잔여 횟수를 사용합니다. 개선 연습 생성·답변·재시도 API가 준비되기 전에는 `improvementPractice`와 연결하지 않습니다.

- `apps/app/src/features/interview/components/InterviewResult.tsx`
- `apps/app/src/features/interview/components/ImprovementPracticeFlow.tsx`

후속 API가 준비되면 다음을 적용합니다.

- “이번 달 개선 연습” → “현재 이용기간 개선 연습”
- 하드코딩된 `13회`, `14회` → `improvementPractice.remaining`
- `remaining === 0`이면 개선 연습 CTA를 잠금 상태로 표시
- `nextResetAt`으로 다음 이용 가능 시점 안내

### 3.3 선택 적용: 세션 기록 화면

아래 화면은 이번 쿼터 전환의 필수 변경 대상은 아닙니다. 다만 2.3의 추가 필드를 표시하려면 API 타입과 UI를 함께 보완합니다.

- `apps/app/src/app/(member)/(interview)/interview-history.tsx`
- `apps/app/src/app/(member)/(interview)/interview-detail.tsx`
- `packages/api/src/interview/getInterviews.ts`
- `packages/api/src/interview/getInterviewSession.ts`

## 4. 호환성 및 확인 기준

- `GET /api/interviews/quota`는 하위 호환 필드 없이 새 계약으로 교체됩니다.
- `POST /api/interviews`의 요청·성공 응답은 유지됩니다.
- 세션 목록·상세의 기존 필드는 유지됩니다.
- 최종 필드 설명과 오류 응답은 백엔드 REST Docs/OpenAPI를 기준으로 확인합니다.
