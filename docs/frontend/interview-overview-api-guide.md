# 면접 Overview API 연동 안내

## 1. 변경 개요

면접 홈에서 주간 활동 요약, 최근 점수 변화, 최근 면접 기록을 여러 API와 임시 계산으로 조합하지 않도록 전용 API를 추가합니다.

프론트는 `GET /api/interviews/overview` 한 번으로 다음 정보를 표시할 수 있습니다.

- 현재 주간 이용기간에 완료한 면접 횟수와 총 연습시간
- 가장 최근 피드백 점수와 직전 점수 대비 변화
- 최근 종료된 면접 3건
- Pro 사용자에게 제공할 다음 연습 포인트

## 2. API 계약

### `GET /api/interviews/overview`

인증이 필요한 API입니다.

```json
{
  "success": true,
  "data": {
    "summary": {
      "periodStart": "2026-08-24T00:00:00+09:00",
      "nextResetAt": "2026-08-31T00:00:00+09:00",
      "completedCount": 3,
      "totalPracticeSeconds": 2400
    },
    "score": {
      "latest": 82,
      "change": 4
    },
    "recentInterviews": [
      {
        "sessionId": "6eb5a11d-f721-42c7-b49b-1157054484f4",
        "position": "백엔드 개발자",
        "experienceLevel": "JUNIOR",
        "interviewType": "TECHNICAL",
        "totalQuestions": 5,
        "status": "COMPLETED",
        "createdAt": "2026-08-27T10:00:00Z",
        "endedAt": "2026-08-27T10:15:00Z",
        "durationSeconds": 900,
        "overallScore": 82,
        "feedbackStatus": "COMPLETED"
      }
    ],
    "nextPracticePoint": "결론을 먼저 말하고 근거를 덧붙이세요."
  },
  "error": null,
  "message": null,
  "timestamp": 1787875200000
}
```

## 3. 필드별 사용 방법

### 3.1 주간 활동 요약

`summary`는 사용자의 구독 시작일을 기준으로 계산된 현재 7일 이용기간입니다. 달력상의 월요일~일요일과 일치하지 않을 수 있습니다.

| 필드 | 화면에서의 의미 | 사용 방법 |
| --- | --- | --- |
| `periodStart` | 현재 이용기간 시작 시각 | 필요하면 기간 표시의 시작값으로 사용 |
| `nextResetAt` | 다음 주간 이용기간 시작 시각 | “다음 리셋” 안내에 사용 |
| `completedCount` | 현재 이용기간에 답변을 끝낸 면접 수 | 주간 완료 횟수로 표시 |
| `totalPracticeSeconds` | 위 면접들의 총 연습시간(초) | 분/시간 단위로 변환해 표시 |

사용자가 마지막 답변까지 제출한 면접은 피드백 생성 중이어도 `completedCount`와 `totalPracticeSeconds`에 포함됩니다. 중간에 나간 `ABANDONED` 면접은 주간 요약에서 제외됩니다.

### 3.2 최근 점수

`score`는 현재 주간이 아니라 전체 면접 이력에서 **종료 시각이 가장 최근인 피드백 완료 면접**을 기준으로 합니다. 피드백 생성이 재시도되거나 늦게 복구되더라도 피드백 저장 시각이 아니라 면접 종료 순서를 사용합니다.

| 필드 | 의미 |
| --- | --- |
| `latest` | 종료 시각이 가장 최근인 피드백 완료 면접의 종합 점수 |
| `change` | 위 면접 점수에서 그다음으로 최근에 종료된 피드백 완료 면접 점수를 뺀 값 |

- 점수가 없으면 `latest=null`, `change=null`입니다.
- 점수가 1개뿐이면 `latest`만 있고 `change=null`입니다.
- `change`는 상승하면 양수, 하락하면 음수, 같으면 `0`입니다.
- `change=null`을 `0`으로 바꾸지 않습니다. 비교할 이전 점수가 없다는 뜻입니다.

### 3.3 최근 면접

`recentInterviews`는 종료 시각이 최신인 면접을 최대 3건 반환합니다. 정상 종료뿐 아니라 사용자가 중간 종료한 `ABANDONED` 면접도 포함될 수 있습니다.

세션 목록 API와 같은 요약 필드 구조를 사용합니다. 배열 순서는 이미 최신순이므로 프론트에서 다시 정렬할 필요가 없습니다.

- `FEEDBACK_PENDING`: 마지막 답변 제출 완료, 피드백 생성 대기 또는 진행 중
- `COMPLETED`: 피드백 생성까지 완료
- `ABANDONED`: 면접 중간 종료

### 3.4 다음 연습 포인트

`nextPracticePoint`는 현재 이용 가능한 플랜이 Pro일 때만 반환됩니다. 면접 종료 순서대로 확인하며, 가장 최근 면접의 피드백에 값이 없으면 그 이전 피드백에 생성된 가장 가까운 값을 반환합니다.

- Free/Basic: 항상 `null`
- Pro: 저장된 값이 있으면 문자열, 없으면 `null`
- 유예기간 안의 `PAST_DUE` Pro: 기존 Pro 권한 기준으로 값 제공

이 값은 별도 생성 요청을 하지 않고, 있으면 표시하고 `null`이면 해당 영역을 숨깁니다.

## 4. 데이터 없음과 오류 처리

처음 가입한 사용자처럼 면접 이력이 없어도 API는 정상 응답합니다.

```json
{
  "summary": {
    "periodStart": "2026-08-24T00:00:00+09:00",
    "nextResetAt": "2026-08-31T00:00:00+09:00",
    "completedCount": 0,
    "totalPracticeSeconds": 0
  },
  "score": {
    "latest": null,
    "change": null
  },
  "recentInterviews": [],
  "nextPracticePoint": null
}
```

`UNPAID` 또는 PAST_DUE 유예기간이 끝난 사용자는 면접 도메인 접근이 제한되며 `402 Payment Required`를 받을 수 있습니다. 이 경우 overview만 비우기보다 기존 구독 제한 화면으로 연결합니다.

## 5. 프론트 수정 체크리스트

- [ ] `GET /api/interviews/overview` API 함수와 응답 타입 추가
- [ ] 면접 홈 진입 시 overview query 호출
- [ ] `totalPracticeSeconds`를 화면 표기 단위로 변환
- [ ] `score.change=null`과 `0`을 구분해 표시
- [ ] `recentInterviews=[]`인 첫 사용자용 빈 상태 처리
- [ ] `ABANDONED`가 최근 면접에 포함될 수 있도록 상태 표시 처리
- [ ] `nextPracticePoint=null`이면 해당 UI 숨김
- [ ] `402` 응답은 기존 구독 제한 흐름으로 처리

최종 필드명과 오류 응답은 백엔드 REST Docs/OpenAPI를 기준으로 확인합니다.
