# 면접 구조화 피드백 코드 리뷰 가이드

## 1. 변경 개요

기존 면접 피드백은 강점과 개선점이 긴 문자열로 저장되어 있었습니다. 이 방식은 화면에서 항목을 나눠 표시하거나, 특정 질문 및 답변 인용과 연결하거나, 개선 연습의 입력으로 사용하기 어렵습니다.

이번 변경에서는 피드백을 다음과 같이 구조화했습니다.

- 종합 점수와 4축 점수를 별도 숫자 필드로 저장합니다.
- 코치 브리핑을 요약·핵심 강점·핵심 개선점으로 구분합니다.
- 강점과 개선점을 각각 독립된 자식 행으로 저장합니다.
- 마지막 답변 제출 시점의 플랜을 피드백 생성 티어로 고정합니다.
- AI 결과가 플랜별 계약을 위반하면 저장하지 않고 최대 3회 다시 생성합니다.
- AI 호출 전에 백엔드 작업 ID를 저장하고, 같은 작업 ID를 가진 worker만 완료·실패할 수 있습니다.
- Free에서 생성한 4축 점수는 유료 전환 전까지 숨기고, 유료 전환 후 영구 공개합니다.
- Basic/Pro에서 생성한 상세 피드백은 이후 Free로 내려가도 계속 제공합니다.

결제 provider와 개선 연습 API 자체는 변경하지 않았습니다.

## 2. 전체 처리 흐름

```text
사용자가 마지막 답변 제출
    ↓
현재 구독 플랜을 feedbackGenerationTier로 세션에 저장
    ↓
트랜잭션 커밋 후 피드백 생성 이벤트 실행
    ↓
백엔드가 feedbackGenerationTaskId 생성
    ↓
PENDING → GENERATING 조건부 전이와 task ID 저장
    ↓
저장된 티어와 대화 내역으로 AI 호출
    ↓
점수·항목 수·필수 값·질문 번호·순위 검증
    ↓
계약 위반 시 전체 피드백을 최대 3회 재생성
    ↓
같은 task ID인지 조건부 UPDATE로 검증
    ↓
소유권이 유지된 경우에만 부모 피드백과 강점·개선점 저장 + 세션 COMPLETED 전이
    ↓
조회 시 생성 티어와 현재 구독 이력에 따라 응답 노출 범위 결정
```

비동기 생성 중 사용자가 플랜을 변경해도 마지막 답변 제출 시 저장한 티어를 계속 사용합니다. 재시도와 서버 복구에서도 현재 플랜을 다시 조회하지 않습니다.

## 3. 스키마와 엔티티

### `V1__initial_schema.sql`

파일: [V1__initial_schema.sql](../../src/main/resources/db/migration/V1__initial_schema.sql)

#### 왜 바뀌었나

문자열 피드백만으로는 항목별 표시, 질문 연결, 답변 인용, 개선 연습 연결을 안정적으로 처리할 수 없습니다. 또한 Free 피드백의 점수 공개 여부를 판단하려면 피드백 생성 시점과 유료 구독 활성화 시점을 비교할 수 있어야 합니다.

#### 무엇이 어떻게 바뀌었나

- `subscription.activated_at`
  - 구독이 최초 활성화된 절대 시각을 저장합니다.
- `interview_session.feedback_generation_tier`
  - 마지막 답변 제출 시점의 플랜을 저장합니다.
- `interview_session.feedback_generation_task_id`
  - AI 호출 전에 백엔드가 발급한 현재 worker의 작업 소유권 UUID를 저장합니다.
  - `GENERATING`이 끝나거나 stale 복구가 실행되면 제거합니다.
- `interview_feedback`
  - 종합 점수, 코치 브리핑, 4축 점수, `next_practice_point`, `generated_tier`를 저장합니다.
- `feedback_strength`
  - 질문 번호, 제목, 상세, 답변 인용, 표시 순서를 저장합니다.
- `feedback_improvement`
  - 안정적인 ID, 추천 순위, 질문 번호, 제목, 요약, 상세, 답변 인용을 저장합니다.
- 기존 `expert_feedbacks`, 문자열 `strengths/improvements`, `detailed_analysis`를 제거했습니다.

#### 리뷰할 부분

- 점수 컬럼의 `1~100` CHECK 제약이 정책과 일치하는지
- `(feedback_id, sort_order)`, `(feedback_id, rank)` 유일 제약이 중복 항목을 방지하는지
- 자식 테이블의 `ON DELETE CASCADE`가 부모 삭제 정책과 맞는지
- Java `int`와 PostgreSQL 스키마 검증이 일치하도록 숫자 컬럼이 `INTEGER`인지
- stale 조회용 `(feedback_status, updated_at)` 인덱스가 복구 Job 쿼리와 일치하는지

### `InterviewSession`

파일: [InterviewSession.java](../../src/main/java/app/mockly/domain/interview/entity/InterviewSession.java)

#### 왜 바뀌었나

피드백 생성은 비동기이므로 AI 응답이 도착하기 전에 사용자의 플랜이 바뀔 수 있습니다. 생성 시점에 현재 플랜을 다시 조회하면 사용자가 답변을 제출했을 때의 정책과 다른 피드백이 만들어질 수 있습니다.

stale 복구 후 새 worker가 시작된 상태에서 이전 worker가 늦게 응답할 수도 있습니다. 세션 상태만 확인하면 이전 worker가 새 worker의 `GENERATING` 상태를 자신의 작업으로 오인할 수 있으므로 작업 ID도 함께 저장합니다.

#### 무엇이 어떻게 바뀌었나

`startFeedbackGeneration(PlanTier generationTier)`가 다음 값을 함께 설정합니다.

- 세션 상태 `FEEDBACK_PENDING`
- 피드백 상태 `PENDING`
- `feedbackGenerationTier`
- 마지막 답변 제출 시각인 `endedAt`
- 현재 피드백 worker의 `feedbackGenerationTaskId`
  - AI 호출 전 `FeedbackGenerationStateService.start()`가 생성해 저장합니다.
  - AI API에는 전달하지 않으며 백엔드 내부 소유권 확인에만 사용합니다.

#### 리뷰할 부분

- 티어 고정과 마지막 답변 저장이 같은 트랜잭션에서 처리되는지
- 재시도 시 `feedbackGenerationTier`가 변경되지 않는지
- `endedAt`이 피드백 완료 시각과 혼용되지 않는지
- 수동 retry에서 이전 task ID가 제거되는지

### `InterviewFeedback`, `FeedbackStrength`, `FeedbackImprovement`

파일:

- [InterviewFeedback.java](../../src/main/java/app/mockly/domain/interview/entity/InterviewFeedback.java)
- [FeedbackStrength.java](../../src/main/java/app/mockly/domain/interview/entity/FeedbackStrength.java)
- [FeedbackImprovement.java](../../src/main/java/app/mockly/domain/interview/entity/FeedbackImprovement.java)

#### 왜 바뀌었나

피드백을 화면과 후속 개선 연습에서 항목 단위로 사용하려면 부모 피드백과 강점·개선점의 관계를 명확하게 표현해야 합니다.

#### 무엇이 어떻게 바뀌었나

- `InterviewFeedback`이 공통 점수와 브리핑을 저장하는 부모 역할을 합니다.
- 강점과 개선점은 `@OneToMany` 자식 컬렉션으로 저장합니다.
- `CascadeType.ALL`로 부모 저장 시 자식도 함께 저장합니다.
- `InterviewFeedback.create()`가 검증을 통과한 AI 결과를 하나의 엔티티 그래프로 변환합니다.

#### 리뷰할 부분

- 부모와 자식의 양방향 참조가 저장 시 올바르게 설정되는지
- 강점은 `sortOrder`, 개선점은 `rank` 기준으로 정렬되는지
- Free 개선점의 `detail`, `quote`가 nullable인지
- 향후 개선 연습이 `FeedbackImprovement.id`를 안정적인 식별자로 사용할 수 있는지

## 4. AI 생성 계약과 검증

### `InterviewFeedbackResult`

파일: [InterviewFeedbackResult.java](../../src/main/java/app/mockly/domain/interview/dto/InterviewFeedbackResult.java)

#### 왜 바뀌었나

AI 출력과 저장 구조를 동일한 형태로 맞춰야 문자열 파싱 없이 검증하고 저장할 수 있습니다.

#### 무엇이 어떻게 바뀌었나

AI 결과를 다음 record로 분리했습니다.

- `CoachBrief`
- `Scores`
- `Strength`
- `Improvement`

`generatedTier`는 AI가 결정하지 않고 서버가 세션에 저장한 값을 사용합니다. `practiceAvailable` 역시 AI 결과가 아니라 응답 시점의 구독 상태로 계산합니다.

#### 리뷰할 부분

- AI가 결정하면 안 되는 서버 정책 필드가 결과 DTO에 포함되어 있지 않은지
- 각 record 필드가 DB와 API 응답에 손실 없이 매핑되는지

### `InterviewAiService`

파일: [InterviewAiService.java](../../src/main/java/app/mockly/domain/interview/service/InterviewAiService.java)

#### 왜 바뀌었나

새 DTO로 역직렬화하려면 AI가 플랜별 구조화 형식을 반환하도록 최소 출력 계약이 필요합니다.

#### 무엇이 어떻게 바뀌었나

- Free는 요약, 4축 점수, 개선점 제목·요약 1건을 생성합니다.
- Basic은 브리핑 전체, 강점 3건, 개선점 3건과 상세·인용을 생성합니다.
- Pro는 Basic 항목과 `nextPracticePoint`를 생성합니다.
- 생성하지 않는 값은 빈 문자열이 아니라 `null`로 요청합니다.

#### 리뷰할 부분

- 현재 변경은 구조화 출력을 위한 최소 계약인지
- 프롬프트 품질 튜닝이 현재 브랜치 범위에 섞이지 않았는지
- Load Test용 `MockInterviewAiService`도 같은 구조를 반환하는지

> 평가 기준, 표현 품질, 인용 방식과 예시 개선은 후속 backlog에서 별도로 진행합니다.

### `FeedbackResultValidator`

파일: [FeedbackResultValidator.java](../../src/main/java/app/mockly/domain/interview/service/FeedbackResultValidator.java)

#### 왜 바뀌었나

JSON 역직렬화 성공은 비즈니스 계약 충족을 보장하지 않습니다. 점수 범위, 항목 수, 질문 번호, 순위가 잘못된 결과는 DB에 저장되기 전에 거절해야 합니다.

#### 무엇이 어떻게 바뀌었나

- 모든 점수를 `1~100`으로 검증합니다.
- 질문 번호가 실제 전체 질문 수 안에 있는지 검증합니다.
- Free의 강점 0건, 개선점 1건을 검증합니다.
- Basic/Pro의 강점·개선점 각 3건을 검증합니다.
- 순서와 순위가 `1..N`이며 중복되지 않는지 검증합니다.
- 필수 문자열과 최대 길이를 검증합니다.
- 생성 대상이 아닌 값은 정확히 `null`인지 검증합니다.
- Pro에서만 `nextPracticePoint`를 허용합니다.

#### 리뷰할 부분

- Java 검증과 DB 제약이 서로 다른 값을 허용하지 않는지
- 빈 문자열과 `null`을 정책에 맞게 구분하는지
- 질문 수가 하드코딩되지 않고 세션의 `totalQuestions`를 사용하는지

## 5. 비동기 처리와 저장 트랜잭션

### `FeedbackGenerationEventHandler`

파일: [FeedbackGenerationEventHandler.java](../../src/main/java/app/mockly/domain/interview/service/FeedbackGenerationEventHandler.java)

#### 왜 바뀌었나

잘못된 AI 결과를 저장하지 않고 재생성해야 하며, 중복 이벤트나 늦게 도착한 worker가 같은 피드백을 중복 저장하지 않아야 합니다.

#### 무엇이 어떻게 바뀌었나

- 조건부 상태 전이로 한 worker만 작업권을 얻습니다.
- `start()`가 반환한 task ID를 AI 재호출 동안 유지하고 `complete()` 또는 `fail()`에 전달합니다.
- `handle()`은 작업 시작과 중복 이벤트 판정만 담당하고, 소유권 획득 이후 흐름은 `processOwnedFeedback()`에 위임합니다.
- `start()` 예외는 좁은 `try-catch`에서 처리하여 소유권을 얻지 못한 세션을 `FAILED`로 변경하지 않습니다.
- 완료와 실패 처리는 `completeOwnedFeedback()`, `handleGenerationFailure()`로 분리했습니다.
- AI 호출 직후 `FeedbackResultValidator`를 실행합니다.
- 호출 또는 검증이 실패하면 전체 결과를 최대 3회 다시 생성합니다.
- 3회 모두 실패하면 세션 피드백 상태를 `FAILED`로 전환합니다.
- 저장이 성공하면 완료 SSE를 전송합니다.

#### 리뷰할 부분

- 중복 이벤트에서 AI가 한 번만 호출되는지
- 부분 결과를 재사용하지 않고 전체 결과를 다시 생성하는지
- 인터럽트 발생 시 스레드 상태를 보존하는지
- 저장 트랜잭션 실패를 일반 AI 실패와 동일하게 처리하지 않는지
- task ID가 일치하지 않는 늦은 worker가 terminal SSE를 전송하지 않는지
- `start()` 예외와 `Optional.empty()`가 각각 실제 오류와 정상적인 작업권 미획득으로 구분되는지

### `FeedbackGenerationStateService`

파일: [FeedbackGenerationStateService.java](../../src/main/java/app/mockly/domain/interview/service/FeedbackGenerationStateService.java)

#### 왜 바뀌었나

AI 호출은 트랜잭션 밖에서 실행해야 하지만, 상태 전이와 피드백 저장은 독립된 짧은 트랜잭션으로 처리해야 합니다. 같은 클래스의 self invocation으로는 Spring의 새 트랜잭션 프록시가 적용되지 않기 때문에 별도 Bean으로 분리했습니다.

#### 무엇이 어떻게 바뀌었나

- `start()`
  - 백엔드가 UUID task ID를 생성합니다.
  - `PENDING → GENERATING` 전이, task ID 저장과 AI 입력 조회를 `REQUIRES_NEW`로 처리합니다.
- `complete()`
  - `GENERATING + 동일 task ID`일 때만 세션 완료 전이와 부모·자식 피드백 저장을 `REQUIRES_NEW`로 처리합니다.
- `fail()`
  - `GENERATING + 동일 task ID`일 때만 최종 생성 실패 상태를 별도 트랜잭션으로 저장합니다.
- 완료·실패가 성공하면 task ID를 제거합니다.

#### 리뷰할 부분

- 자식 저장 실패 시 세션 완료 전이도 함께 롤백되는지
- 늦게 완료된 worker가 기존 결과를 덮어쓰지 않는지
- 작업 선점과 context 조회가 유일한 `sessionId`를 기준으로 일관되게 처리되는지
- AI 호출과 최대 3회 내부 재생성 동안 같은 task ID를 사용하는지

### `StaleFeedbackRecoveryJob`

파일: [StaleFeedbackRecoveryJob.java](../../src/main/java/app/mockly/domain/interview/service/StaleFeedbackRecoveryJob.java)

#### 왜 바뀌었나

stale 세션을 조회한 뒤 엔티티 상태를 바로 변경하면, 조회 직후 정상 worker가 완료하거나 다른 서버가 먼저 복구한 상태를 덮어쓸 수 있습니다.

#### 무엇이 어떻게 바뀌었나

- stale `GENERATING`은 조회 당시 상태, threshold, task ID가 모두 같은 경우에만 `PENDING`으로 되돌립니다.
- stale `PENDING`도 threshold 조건부 UPDATE가 성공한 경우에만 다시 발행합니다.
- 조건부 UPDATE에 성공한 Job만 새 `FeedbackRequestedEvent`를 발행합니다.
- 복구 시 이전 task ID와 실패 사유를 제거합니다.

#### 리뷰할 부분

- 여러 서버의 Job이 같은 세션을 조회해도 한 곳만 이벤트를 발행하는지
- stale 조회 후 정상 완료된 세션이 다시 `PENDING`으로 바뀌지 않는지
- 이전 worker의 완료·실패가 새 worker의 task ID와 일치하지 않아 거절되는지

## 6. 티어별 노출 정책

### `Subscription`, `SubscriptionRepository`

파일:

- [Subscription.java](../../src/main/java/app/mockly/domain/product/entity/Subscription.java)
- [SubscriptionRepository.java](../../src/main/java/app/mockly/domain/product/repository/SubscriptionRepository.java)

#### 왜 바뀌었나

Free 피드백의 4축 점수를 영구 공개하려면 피드백 생성 이후 유료 구독이 실제로 활성화된 적이 있는지 확인해야 합니다. 기존 `LocalDateTime startedAt`은 서버 시간대 해석과 재활성화 의미가 섞일 수 있어 절대 시각 비교에 적합하지 않습니다.

#### 무엇이 어떻게 바뀌었나

- `Subscription.activate()`에서 최초 활성화 시 `Instant activatedAt`을 기록합니다.
- 유료 상품의 `activatedAt`이 피드백 생성 시각 이후인지 확인하는 repository query를 추가했습니다.
- Free 구독 이력은 조회 대상에서 제외합니다.

#### 리뷰할 부분

- `activatedAt`이 재활성화 때 덮어써지지 않는지
- 최종 구독 상태와 무관하게 과거 유료 활성화 이력을 찾는 것이 영구 공개 정책과 맞는지
- 시간대 변환 없이 `Instant`끼리 비교하는지

### `FeedbackVisibilityService`

파일: [FeedbackVisibilityService.java](../../src/main/java/app/mockly/domain/interview/service/FeedbackVisibilityService.java)

#### 왜 바뀌었나

DB에 저장된 피드백과 현재 사용자에게 보여줄 피드백의 범위가 다릅니다. 이 정책을 Controller나 DTO에 흩어놓으면 단독 피드백 조회와 세션 상세 응답이 달라질 수 있습니다.

#### 무엇이 어떻게 바뀌었나

- Basic/Pro에서 생성된 점수는 항상 공개합니다.
- Free에서 생성된 점수는 이후 유료 활성화 이력이 있을 때만 공개합니다.
- 점수 공개 기준 시각은 마지막 답변 제출 시각인 `session.endedAt`입니다.
- 현재 플랜이 Pro인지 계산해 개선 연습 자격 판단에 전달합니다.

#### 리뷰할 부분

- 과거 Free 피드백 공개 기준이 `endedAt` 이후 유료 활성화인지
- `endedAt`이 없는 레거시 데이터에서만 `feedback.createdAt`을 fallback으로 쓰는지
- 모든 사용자에게 현재 Free 또는 유료 구독 행이 있다는 불변식에 의존해도 되는지

### `FeedbackDto`

파일: [FeedbackDto.java](../../src/main/java/app/mockly/domain/interview/dto/response/FeedbackDto.java)

#### 왜 바뀌었나

동일한 저장 데이터라도 현재 구독과 생성 티어에 따라 일부 필드를 숨기거나 개선 연습 가능 여부를 계산해야 합니다.

#### 무엇이 어떻게 바뀌었나

- 잠긴 점수는 `scores=null`로 반환합니다.
- 생성하지 않은 단일 값은 `null`로 반환합니다.
- 제공할 강점이 없으면 `strengths=[]`를 반환합니다.
- `practiceAvailable`은 현재 Pro이고 해당 개선점에 `detail`이 있을 때만 `true`입니다.
- 쿼터 잔여량은 포함하지 않습니다.

#### 리뷰할 부분

- 숨김 규칙이 `null`과 빈 배열 계약을 지키는지
- `practiceAvailable`에 quota 값이 섞이지 않았는지
- 개선점 ID가 API 응답에서 누락되지 않는지

## 7. API 연결

### `InterviewService`

파일: [InterviewService.java](../../src/main/java/app/mockly/domain/interview/service/InterviewService.java)

#### 왜 바뀌었나

피드백 단독 조회와 세션 상세가 서로 다른 노출 정책을 적용하면 동일 피드백의 응답이 달라질 수 있습니다.

#### 무엇이 어떻게 바뀌었나

- 마지막 답변 제출 시 현재 티어를 세션에 고정합니다.
- `getFeedback()`가 `FeedbackVisibilityService`를 사용합니다.
- `getSessionDetail()`도 같은 `FeedbackVisibilityService`를 사용합니다.

#### 리뷰할 부분

- 두 조회 API가 동일한 `FeedbackDto` 생성 경로를 사용하는지
- `PENDING`, `GENERATING`, `FAILED`, `COMPLETED`의 기존 HTTP 계약이 유지되는지

## 8. 테스트 확인 순서

다음 순서로 테스트를 보면 정책을 빠르게 검토할 수 있습니다.

1. `FeedbackResultValidatorTest`
   - 플랜별 계약, 점수, 질문 번호, null 규칙
2. `FeedbackGenerationEventHandlerTest`
   - 중복 이벤트, 재생성, 3회 실패
3. `FeedbackGenerationStateServiceTest`
   - task ID 발급, 늦은 worker의 완료·실패 거절, 저장 실패
4. `FeedbackGenerationOwnershipIntegrationTest`
   - stale 복구 후 신규 worker 소유권과 이전 worker의 조건부 UPDATE 거절
5. `StaleFeedbackRecoveryJobTest`
   - threshold, task ID, 중복 복구 이벤트 차단
6. `FeedbackGenerationTransactionIntegrationTest`
   - 피드백 저장 실패 시 완료 전이와 task ID 제거가 함께 롤백되는지
7. `InterviewFeedbackTest`
   - 부모와 자식 엔티티 매핑
8. `FeedbackVisibilityServiceTest`
   - Free 잠금, 유료 전환, 다운그레이드, PAST_DUE Pro
9. `InterviewControllerTest`
   - 실제 구조화 JSON과 제거 필드
10. `FlywaySchemaMigrationTest`
   - 신규 테이블·task ID 컬럼·stale 조회 인덱스와 Legacy 컬럼 제거

전체 회귀 검증 명령은 다음과 같습니다.

```bash
./gradlew test
./gradlew openapi3
git diff --check
```

## 9. 배포 전 확인사항

- 프론트가 구·신 피드백 응답을 모두 처리하는 버전을 먼저 배포했는지 확인합니다.
- preprod의 `interview_session`, `interview_feedback`, 자식 피드백 row count를 다시 확인합니다.
- 기존 피드백 데이터가 발견되면 Flyway V1 적용을 중단하고 변환 계획을 먼저 세웁니다.
- PostgreSQL 빈 DB에 Flyway V1을 적용한 뒤 Hibernate `ddl-auto=validate`가 통과하는지 확인합니다.
- Free 잠금, 유료 전환 후 공개, 다운그레이드 후 상세 유지 화면을 순서대로 확인합니다.
- 평가 품질을 위한 AI 프롬프트 개선은 현재 브랜치와 분리합니다.
