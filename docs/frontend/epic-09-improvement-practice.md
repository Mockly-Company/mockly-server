# Epic #9 — Pro 전용 답변 개선 연습 (Improvement Practice) API 안내

## 1. 변경 개요

- **목적**: 모바일 앱(`@mockly/api` 및 `ImprovementPracticeScreen`)에 선구현되어 있던 Pro 플랜 전용 답변 개선 연습 5개 엔드포인트와 비동기 AI 비교 평가 파이프라인을 백엔드에 구현했습니다.
- **변경 범위**:
  - `POST /api/interviews/improvement-practices` — 개선 연습 세션 생성 (`PENDING`, 생성 시 쿼터 미차감)
  - `GET /api/interviews/improvement-practices/{practiceId}` — 연습 상세 및 비교 평가 결과 조회
  - `POST /api/interviews/improvement-practices/{practiceId}/answer` — 개선 답변 제출, 주간 쿼터(`IMPROVEMENT_PRACTICE`) 1회 차감 및 비동기 비교 평가 시작 (`GENERATING`)
  - `GET /api/interviews/improvement-practices/{practiceId}/events` — SSE 상태 이벤트 스트림 (`event: status`)
  - `POST /api/interviews/improvement-practices/{practiceId}/retry` — 실패(`FAILED`) 건 추가 쿼터 차감 없이 재시도
- **제외 항목**:
  - 개선 연습 히스토리 목록 API (현 모바일 범위 외, 추후 필요 시 별도 엔드포인트로 확장)

---

## 2. API 변경 사항

### 2.1 `POST /api/interviews/improvement-practices` (신규)

- **요청 본문**:
  ```json
  {
    "improvementId": 42
  }
  ```
- **성공 응답 (`201 Created`)**:
  ```json
  {
    "success": true,
    "data": {
      "id": 101,
      "status": "PENDING"
    },
    "error": null,
    "message": null,
    "timestamp": 1762905600000
  }
  ```
- **오류 응답**:
  - `403 FORBIDDEN`: Pro 플랜이 아니거나(`weeklyImprovementPracticeLimit <= 0`) 본인 소유의 개선 포인트가 아닌 경우
  - `404 RESOURCE_NOT_FOUND`: `improvementId`가 존재하지 않는 경우
  - `429 QUOTA_EXCEEDED`: 주간 답변 개선 연습 잔여 쿼터가 0인 경우

### 2.2 `GET /api/interviews/improvement-practices/{practiceId}` (신규)

- **성공 응답 (`200 OK`, `COMPLETED` 예시)**:
  ```json
  {
    "success": true,
    "data": {
      "id": 101,
      "status": "COMPLETED",
      "questionNumber": 1,
      "questionText": "트래픽 급증 상황에서 DB 병목을 해결한 경험을 설명해 주세요.",
      "originalAnswer": "인덱스를 추가해서 해결했습니다.",
      "submittedAnswer": "복합 인덱스 적용으로 p95 지연을 820ms에서 140ms로 단축했습니다.",
      "result": {
        "score": 88,
        "summary": "장애 원인과 해결 수치가 명확해져 설득력이 크게 향상되었습니다.",
        "detail": "기존 답변에서 누락되었던 정량 지표를 명확히 제시하여 실무 역량이 잘 드러납니다.",
        "quote": "복합 인덱스 적용으로 p95 지연을 820ms에서 140ms로 단축했습니다."
      }
    },
    "error": null,
    "message": null,
    "timestamp": 1762905600000
  }
  ```
  - `PENDING`, `GENERATING`, `FAILED` 상태에서는 `result`가 `null`로 반환됩니다.

### 2.3 `POST /api/interviews/improvement-practices/{practiceId}/answer` (신규)

- **요청 본문**:
  ```json
  {
    "answer": "복합 인덱스 적용으로 p95 지연을 820ms에서 140ms로 단축했습니다."
  }
  ```
  - 검증: 공백 불가, 최대 `1,500`자 (`@Size(max = 1500)`)
- **성공 응답 (`200 OK`)**:
  ```json
  {
    "success": true,
    "data": {
      "id": 101,
      "status": "GENERATING"
    },
    "error": null,
    "message": null,
    "timestamp": 1762905600000
  }
  ```
- **정책**: 답변 제출 트랜잭션에서 주간 `IMPROVEMENT_PRACTICE` 쿼터 1회를 차감하고 커밋 직후 비동기 AI 비교 평가를 실행합니다.

### 2.4 `GET /api/interviews/improvement-practices/{practiceId}/events` (신규 SSE)

- **응답 헤더**: `Content-Type: text/event-stream`
- **이벤트 포맷**:
  ```text
  event: status
  data: {"practiceStatus":"completed"}
  ```
  또는 실패 시:
  ```text
  event: status
  data: {"practiceStatus":"failed","message":"답변 개선 평가 생성에 실패했습니다."}
  ```
- 이미 `COMPLETED` 또는 `FAILED`인 연습에 연결하면 즉시 `status` 이벤트를 1회 전송하고 스트림을 정상 종료합니다.

### 2.5 `POST /api/interviews/improvement-practices/{practiceId}/retry` (신규)

- **성공 응답 (`200 OK`)**: `GET /api/interviews/improvement-practices/{practiceId}`와 동일한 스키마(`status: "GENERATING"`, `result: null`)를 반환하며 추가 주간 쿼터를 차감하지 않습니다.
- `FAILED` 상태가 아닌 연습에 호출하면 `400 VALIDATION_ERROR`를 반환합니다.

---

## 3. 프론트 수정 대상

- **이번 배포 필수**:
  - `@mockly/api`의 `postImprovementPractice`, `getImprovementPractice`, `postImprovementPracticeAnswer`, `streamImprovementPracticeEvents`, `postImprovementPracticeRetry` 계약과 필드·상태값(`PENDING` / `GENERATING` / `COMPLETED` / `FAILED` 및 SSE 소문자 `completed` / `failed`)이 1:1 일치하므로 API 레이어 수정 없이 연동 가능합니다.
- **후속 API 대기**: 없음
- **선택 개선 항목**:
  - `GET /api/interviews/improvement-practices/{practiceId}` 응답에 `questionNumber`, `questionText`, `originalAnswer`, `submittedAnswer`가 추가로 제공되므로 앱 재진입/복구 시 서버 스냅샷을 그대로 표시할 수 있습니다.

---

## 4. 호환성 및 확인 기준

- **Breaking Change**: 없음 (신규 엔드포인트 추가 및 기존 `GET /api/interviews/quota`의 `improvementPractice` 쿼터 연동)
- **유지 계약**:
  - 기존 면접 진행·피드백 생성·주간 면접 쿼터(`QuotaType.INTERVIEW`) 동작은 변경 없이 유지됩니다.
  - 미납 구독(`UNPAID`) 사용자는 기존 `/api/interviews/**` 접근 제어와 동일하게 `402 SUBSCRIPTION_UNPAID`가 반환됩니다.
- **계약 기준**: Spring REST Docs 및 `./gradlew openapi3`로 생성된 `openapi3.yaml`을 최종 필드 계약 기준으로 삼습니다.
