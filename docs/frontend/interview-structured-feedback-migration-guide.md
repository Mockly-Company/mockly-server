# 면접 구조화 피드백 API 변경 안내

## 1. 변경 개요

### 왜 변경하나요

기존 피드백은 강점과 개선점이 긴 문자열로 전달되어 프론트에서 제목, 설명, 관련 질문, 답변 인용을 구분하기 어려웠습니다. 플랜에 따라 보여줄 수 있는 정보도 달라 문자열 일부를 안전하게 잠그기 어려웠습니다.

새 응답은 화면에서 바로 사용할 수 있도록 피드백을 항목별 객체와 배열로 제공합니다.

- 답변 구조·구체성·직무 연관성·전달 명확성의 4축 점수
- 코치 브리핑의 요약·핵심 강점·핵심 개선점
- 질문 번호와 답변 인용이 포함된 강점 목록
- 추천 순위와 개선 연습 ID가 포함된 개선점 목록
- 개선 연습 진입 자격을 나타내는 `practiceAvailable`

### 어떤 API가 바뀌나요

다음 두 응답의 `feedback` 객체가 동일한 구조로 변경됩니다.

- `GET /api/interviews/{sessionId}/feedback`
- `GET /api/interviews/{sessionId}`

피드백 상태 `PENDING | GENERATING | COMPLETED | FAILED`, HTTP 상태 코드와 SSE 이벤트 규약은 변경되지 않습니다. 개선 연습 시작 API는 이번 변경에 포함되지 않습니다.

### 프론트는 무엇을 바꿔야 하나요

- 기존 문자열 피드백 모델을 신규 구조화 모델과 함께 읽을 수 있게 변경합니다.
- 프론트가 먼저 구·신 응답을 모두 지원한 뒤 백엔드를 배포합니다.
- 점수와 상세 값의 `null`, 목록의 빈 배열 `[]`을 서로 다르게 처리합니다.

## 2. 피드백 객체 변경

### 왜 기존 필드를 제거하나요

기존 필드는 하나의 문자열 안에 여러 의미가 섞여 있어 항목별 UI, 답변 인용 UI, 개선 연습 진입점을 안정적으로 만들기 어렵습니다.

### API가 어떻게 바뀌나요

| 기존 필드 | 신규 필드 | 프론트 변경사항 |
| --- | --- | --- |
| `feedback.expertFeedbacks` | `feedback.scores` | 4축 점수 객체로 표시합니다. `null`이면 잠금 UI를 표시합니다. |
| `feedback.strengths` 문자열 | `feedback.strengths[]` | 배열을 `sortOrder` 순서로 렌더링합니다. |
| `feedback.improvements` 문자열 | `feedback.improvements[]` | 배열을 `rank` 순서로 렌더링합니다. |
| `feedback.detailedAnalysis` | 각 항목의 `detail`, `quote` | 항목 내부 상세와 답변 인용으로 표시합니다. |

### 신규 응답 예시

```json
{
  "overallScore": 82,
  "generatedTier": "BASIC",
  "coachBrief": {
    "summary": "핵심 결론을 먼저 말하면 답변이 더 명확해집니다.",
    "keyStrength": "실무 경험을 구체적으로 설명했습니다.",
    "keyImprovement": "결론을 먼저 제시해 보세요."
  },
  "scores": {
    "structure": 78,
    "specificity": 85,
    "jobRelevance": 81,
    "clarity": 76
  },
  "strengths": [
    {
      "id": 1,
      "questionNumber": 2,
      "title": "구체적인 장애 대응 경험",
      "detail": "문제와 해결 과정을 단계적으로 설명했습니다.",
      "quote": "커넥션 풀 고갈 원인을 먼저 확인했습니다.",
      "sortOrder": 1
    }
  ],
  "improvements": [
    {
      "id": 10,
      "rank": 1,
      "questionNumber": 1,
      "title": "결론부터 답변하기",
      "summary": "핵심 결론을 먼저 제시하세요.",
      "detail": "배경 설명보다 선택한 결론을 먼저 말하면 좋습니다.",
      "quote": "우선 당시 상황을 설명드리면...",
      "practiceAvailable": false
    }
  ],
  "nextPracticePoint": null
}
```

`generatedTier`는 피드백을 생성한 당시 플랜입니다. 현재 사용자의 구독 플랜과 다를 수 있으므로 현재 권한 판정에 사용하면 안 됩니다.

## 3. 플랜별 응답 차이

### 왜 플랜별 데이터가 다른가요

Free는 생성 비용을 줄이기 위해 요약형 피드백만 생성합니다. 4축 점수는 저장하지만 유료 전환 전까지 숨깁니다. Basic과 Pro는 생성 당시 상세 내용을 저장하므로 이후 Free로 내려가도 이미 생성된 내용은 유지됩니다.

### Free에서 생성한 피드백

#### API가 어떻게 바뀌나요

- `overallScore`는 항상 제공됩니다.
- `coachBrief.summary`는 제공됩니다.
- `scores`는 유료 전환 전까지 `null`입니다.
- `strengths`는 `[]`입니다.
- `improvements`는 제목과 요약이 있는 1건입니다.
- `coachBrief.keyStrength`, `coachBrief.keyImprovement`, 개선점 `detail`, `quote`, `nextPracticePoint`는 `null`입니다.
- 유료 전환 후에는 과거 Free 피드백의 `scores`가 영구 공개됩니다.
- 유료 사용 이력이 있어도 Free로 돌아온 후 새로 생성한 Free 피드백의 `scores`는 다시 잠깁니다. 해당 피드백 생성 후 다시 유료로 전환해야 공개됩니다.
- 유료 전환 후에도 생성하지 않았던 상세·인용은 새로 생기지 않습니다.

#### 프론트는 무엇을 바꿔야 하나요

- `scores === null`이면 점수 차트를 빈 값으로 그리지 말고 잠금 UI를 표시합니다.
- `strengths.length === 0`이면 강점 영역을 숨기거나 Free 안내 UI로 대체합니다.
- 개선점의 `detail`, `quote`가 `null`이면 상세 영역을 만들지 않습니다.
- 과거 화면을 다시 열었을 때 `scores`가 공개될 수 있으므로 최초 응답을 영구 캐시하지 않습니다.
- 현재 Free라면 과거 유료 이력만으로 새 Free 피드백의 점수를 미리 열지 말고, API의 `scores` 값을 기준으로 잠금 UI를 표시합니다.

### Basic 또는 Pro에서 생성한 피드백

#### API가 어떻게 바뀌나요

- 4축 점수와 코치 브리핑 전체가 제공됩니다.
- 강점 3건과 개선점 3건의 상세·인용이 제공됩니다.
- Pro에서 생성한 피드백만 `nextPracticePoint`가 존재합니다.
- 이후 Free로 내려가도 이미 생성된 상세 피드백은 계속 제공됩니다.

#### 프론트는 무엇을 바꿔야 하나요

- 상세 피드백 노출을 현재 플랜만으로 다시 잠그지 않습니다.
- 응답에 상세 값이 있으면 그대로 표시합니다.
- `nextPracticePoint !== null`일 때만 다음 연습 포인트 UI를 표시합니다.

## 4. `practiceAvailable`

### 왜 필요한가요

개선점 상세가 있다고 해서 모든 사용자가 개선 연습을 시작할 수 있는 것은 아닙니다. 현재 구독 권한과 해당 개선점의 데이터 존재 여부를 백엔드가 함께 판정해 프론트에 전달합니다.

### API가 어떻게 바뀌나요

각 `improvements[]` 항목에 `practiceAvailable`이 추가됩니다.

다음 조건을 모두 만족할 때만 `true`입니다.

- 현재 구독이 Pro 또는 유예기간 안의 `PAST_DUE Pro`
- 해당 개선점에 `detail`이 존재함

`practiceAvailable`에는 주간 개선 연습 잔여량이 포함되지 않습니다.

### 프론트는 무엇을 바꿔야 하나요

개선 연습 버튼의 최종 활성 조건은 다음과 같습니다.

```text
improvement.practiceAvailable === true
AND quota.improvementPractice.remaining > 0
```

- `practiceAvailable=false`이면 권한 또는 데이터가 없는 상태로 처리합니다.
- `practiceAvailable=true`이지만 잔여량이 0이면 쿼터 소진 상태로 처리합니다.
- 실제 개선 연습 시작 API는 후속 작업이므로 현재는 진입 UI 정책만 준비합니다.

## 5. null과 빈 배열 처리

### 왜 구분해야 하나요

`null`은 해당 값이 잠겼거나 생성되지 않았다는 뜻이고, `[]`는 제공할 항목이 없다는 뜻입니다. 빈 객체나 빈 문자열로 치환하면 잠금 상태와 데이터 없음 상태를 구분하기 어렵습니다.

### API 규칙

| 응답 형태 | 의미 | 프론트 처리 |
| --- | --- | --- |
| `scores: null` | 4축 점수가 잠김 | 점수 잠금 UI |
| `nextPracticePoint: null` | 생성하지 않은 값 | 해당 UI 숨김 |
| `strengths: []` | 제공할 강점 없음 | 목록 영역 숨김 또는 안내 표시 |
| `detail: null` | 상세를 생성하지 않음 | 상세·인용 영역 숨김 |

객체가 `null`인 경우 그 하위 필드에 접근하지 않도록 모델과 렌더링 로직을 작성합니다.

## 6. 구·신 응답 호환

### 왜 프론트를 먼저 배포하나요

백엔드가 먼저 배포되면 구버전 프론트는 문자열을 기대하다가 배열과 객체를 받아 피드백 화면을 정상적으로 렌더링하지 못할 수 있습니다.

### 응답을 어떻게 구분하나요

```text
generatedTier 필드가 있으면 신규 구조화 응답
generatedTier 필드가 없으면 기존 문자열 응답
```

### 프론트는 무엇을 바꿔야 하나요

1. 신규 구조화 모델을 추가합니다.
2. 기존 문자열 모델을 바로 삭제하지 않고 전환 기간 동안 함께 유지합니다.
3. `generatedTier` 존재 여부로 신규·기존 렌더러를 선택합니다.
4. 신규 대응 버전을 먼저 배포하고 기존 응답으로 화면을 확인합니다.
5. 백엔드 배포 후 Free와 유료 구조화 응답을 확인합니다.
6. 안정화가 확인된 후 Legacy 렌더링 코드를 제거합니다.

## 7. 프론트 수정 체크리스트

- [ ] 피드백 모델에 `generatedTier`, `coachBrief`, `scores`를 추가했습니다.
- [ ] `strengths`, `improvements`를 문자열이 아닌 배열로 처리합니다.
- [ ] `scores`와 상세 필드의 `null`을 허용합니다.
- [ ] `scores=null`일 때 잠금 UI를 표시합니다.
- [ ] Basic/Pro 생성 피드백을 현재 Free라는 이유로 다시 숨기지 않습니다.
- [ ] `practiceAvailable`과 quota 잔여량을 함께 확인합니다.
- [ ] `generatedTier`로 현재 구독 권한을 판정하지 않습니다.
- [ ] 구·신 응답을 모두 읽을 수 있는 버전을 백엔드보다 먼저 배포합니다.

화면 구현의 최종 필드 기준은 최신 REST Docs/OpenAPI입니다.

## 8. 배포 확인 순서

1. 프론트가 기존 응답과 신규 응답을 모두 처리하는 버전을 배포합니다.
2. 기존 피드백 응답으로 현재 화면이 정상인지 확인합니다.
3. 백엔드 구조화 피드백 버전을 배포합니다.
4. Free 신규 피드백의 점수 잠금과 요약형 화면을 확인합니다.
5. Basic/Pro 상세 피드백 화면을 확인합니다.
6. 유료 전환 후 과거 Free 점수가 공개되는지 확인합니다.
7. Free로 내려간 뒤에도 과거 유료 상세 피드백이 유지되는지 확인합니다.
