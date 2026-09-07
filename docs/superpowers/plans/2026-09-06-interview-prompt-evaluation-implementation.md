# Interview Prompt Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 현재 면접 프롬프트를 텍스트 리소스로 분리하고, LangSmith 오프라인 평가에서 승인된 개선 프롬프트만 운영에 반영한다.

**Architecture:** `InterviewAiService`의 AI 호출 책임은 유지하고 프롬프트 조립만 concrete `InterviewPromptFactory`로 분리한다. 기존 프롬프트는 운영 리소스, 개선안은 평가 전용 후보 리소스로 관리하며 동일 데이터·모델 설정으로 비교한 뒤 사람 승인을 거쳐 운영 리소스를 교체한다.

**Tech Stack:** Java 21, Spring Boot 3.5.7, Spring AI 1.1.4 `ChatClient`, JUnit 5, Mockito, LangSmith UI

**Spec:** `docs/superpowers/specs/2026-09-06-interview-prompt-evaluation-design.md`

## Global Constraints

- 이번 구현에서 운영 Validator를 추가하지 않는다.
- 질문 SSE 전송·저장 방식과 피드백 생성·재시도 방식은 변경하지 않는다.
- LangChain과 LangSmith를 운영 런타임 의존성으로 추가하지 않는다.
- 운영 프롬프트의 단일 원본은 `src/main/resources/prompts/interview/`이다.
- `InterviewPromptFactory`는 interface 없이 `@Component` 구체 클래스 하나로 구현한다.
- LangSmith의 기존 데이터셋·프롬프트·평가기 설정을 수정하거나 삭제하지 않는다.
- LangSmith Judge는 `gpt-5.5`, 품질은 1~5점, 중대 오류는 별도 평가기로 사용한다.
- 익명화와 사람 검토가 끝난 데이터만 LangSmith에 업로드한다.
- 커밋은 사용자가 명시적으로 요청한 경우에만 수행하고, 작업 단계에서는 대상과 메시지만 제안한다.

---

## File Map

### 운영 코드

- `src/main/java/app/mockly/domain/interview/dto/InterviewPrompt.java`: 완성된 system/user 프롬프트 값 객체
- `src/main/java/app/mockly/domain/interview/service/InterviewPromptFactory.java`: 텍스트 리소스 로드와 입력값 주입
- `src/main/java/app/mockly/domain/interview/service/InterviewAiService.java`: 팩토리가 만든 프롬프트로 기존 AI 호출 수행
- `src/main/resources/prompts/interview/*.txt`: 첫 질문·후속 질문·피드백의 system/user 운영 템플릿

### 평가 자산

- `evals/interview/prompts/candidate/*.txt`: 운영에 아직 반영하지 않은 후보 템플릿
- `evals/interview/schema/evaluation-case.schema.json`: 평가 데이터 공통 형식
- `evals/interview/datasets/first-question.jsonl`: 첫 질문 15건
- `evals/interview/datasets/follow-up-question.jsonl`: 후속 질문 25건
- `evals/interview/datasets/feedback.jsonl`: 피드백 20건
- `docs/review/interview-prompt-evaluation-guide.md`: 익명화·실험·승인 기록

---

### Task 1: 기존 프롬프트를 동작 변경 없이 리소스로 분리

**Files:**

- Create: `src/main/java/app/mockly/domain/interview/dto/InterviewPrompt.java`
- Create: `src/main/java/app/mockly/domain/interview/service/InterviewPromptFactory.java`
- Create: `src/main/resources/prompts/interview/first-question-system.txt`
- Create: `src/main/resources/prompts/interview/first-question-user.txt`
- Create: `src/main/resources/prompts/interview/follow-up-question-system.txt`
- Create: `src/main/resources/prompts/interview/follow-up-question-user.txt`
- Create: `src/main/resources/prompts/interview/feedback-system.txt`
- Create: `src/main/resources/prompts/interview/feedback-user.txt`
- Modify: `src/main/java/app/mockly/domain/interview/service/InterviewAiService.java`
- Test: `src/test/java/app/mockly/domain/interview/service/InterviewPromptFactoryTest.java`

**Interfaces:**

- Produces: `record InterviewPrompt(String system, String user)`
- Produces: concrete `InterviewPromptFactory`
- Produces: `InterviewPrompt firstQuestion(InterviewSession session)`
- Produces: `InterviewPrompt followUpQuestion(InterviewSession session, List<InterviewMessage> history)`
- Produces: `InterviewPrompt feedback(List<InterviewMessage> history, InterviewType type, PlanTier tier)`
- Does not produce: `InterviewPromptFactory` interface

- [x] **Step 1: 프롬프트 조립 테스트를 먼저 작성**

```java
@Test
void rendersFirstQuestionContext() {
    InterviewPrompt prompt = factory.firstQuestion(firstQuestionSession("백엔드", "JPA"));

    assertThat(prompt.system()).contains("키워드 [JPA]");
    assertThat(prompt.user()).contains("포지션: 백엔드", "탐색 키워드: JPA");
}

@Test
void rendersFollowUpHistoryWithStableRoles() {
    InterviewPrompt prompt = factory.followUpQuestion(session, List.of(question, answer));

    assertThat(prompt.user())
            .contains("\"role\":\"interviewer\"")
            .contains("\"role\":\"candidate\"");
}

@ParameterizedTest
@EnumSource(PlanTier.class)
void rendersTheExistingFeedbackContractForEachTier(PlanTier tier) {
    InterviewPrompt prompt = factory.feedback(history, InterviewType.TECHNICAL, tier);

    assertThat(prompt.system()).contains(expectedTierContract(tier));
}
```

테스트 fixture는 현재 `InterviewAiService`에 전달되는 값과 동일한 session/history를 만든다. `expectedTierContract`는 Free의 빈 strengths·상세 null, Basic의 상세 3건·`nextPracticePoint=null`, Pro의 상세 3건·`nextPracticePoint` 생성 문구를 각각 반환한다.

- [x] **Step 2: 새 타입 부재로 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "app.mockly.domain.interview.service.InterviewPromptFactoryTest"`

Expected: `InterviewPrompt`와 `InterviewPromptFactory`를 찾지 못해 FAIL

- [x] **Step 3: 기존 문자열을 여섯 텍스트 파일로 그대로 이동**

첫 질문, 후속 질문, 피드백마다 system/user 파일을 만든다. 내용과 변수 순서는 현재 `InterviewAiService`와 동일하게 유지한다. 이 단계에서는 문구를 개선하지 않는다.

- [x] **Step 4: concrete 팩토리와 값 객체 구현**

```java
public record InterviewPrompt(String system, String user) {}
```

`InterviewPromptFactory`는 `@Component` 구체 클래스로 만들고 interface는 만들지 않는다. 여섯 템플릿은 생성 시 `ClassPathResource#getContentAsString(StandardCharsets.UTF_8)`로 한 번 읽어 필드에 보관하고, 요청마다 파일을 다시 읽지 않는다. 공개 메서드는 기존 변수 순서대로 `formatted`를 적용하며, 대화 내역은 기존처럼 `ObjectMapper`로 `interviewer/candidate` 역할 배열을 직렬화한다.

- [x] **Step 5: AI 서비스는 조립된 값만 사용하도록 변경**

```java
InterviewPrompt prompt = interviewPromptFactory.firstQuestion(session);
return chatClient.prompt()
        .system(prompt.system())
        .user(prompt.user())
        .stream()
        .content();
```

후속 질문과 피드백도 팩토리의 대응 메서드를 호출한다. 키워드 추출은 사용자 노출 프롬프트 평가 대상이 아니므로 현재 위치와 동작을 유지한다.

- [x] **Step 6: 리소스화 전후 동작 회귀 테스트 실행**

Run: `./gradlew test --tests "app.mockly.domain.interview.service.InterviewPromptFactoryTest" --tests "app.mockly.domain.interview.service.FeedbackGenerationEventHandlerTest" --tests "app.mockly.domain.interview.controller.InterviewControllerTest"`

Expected: PASS. 질문 SSE 이벤트와 피드백 재시도 횟수는 변경되지 않는다.

- [x] **Step 7: 커밋 체크포인트 제안**

Suggested message: `refactor: 면접 AI 프롬프트 리소스 분리`

### Task 2: 평가 전용 후보 프롬프트 작성

**Files:**

- Create: `evals/interview/prompts/candidate/first-question-system.txt`
- Create: `evals/interview/prompts/candidate/first-question-user.txt`
- Create: `evals/interview/prompts/candidate/follow-up-question-system.txt`
- Create: `evals/interview/prompts/candidate/follow-up-question-user.txt`
- Create: `evals/interview/prompts/candidate/feedback-system.txt`
- Create: `evals/interview/prompts/candidate/feedback-user.txt`
- Test: `src/test/java/app/mockly/domain/interview/evaluation/InterviewPromptCandidateContractTest.java`

GPT-5.4 계열 공식 가이드에 맞춰 system 템플릿을 `role`, `objective`, 입력·근거 규칙, 판단 절차, 출력 계약과 검증의 XML 의미 블록으로 구성한다. user 템플릿은 동적 자기소개와 JSON 대화를 데이터 블록으로 격리한다. 운영 승격 전까지 production 템플릿과 런타임 호출 흐름은 변경하지 않는다.

**Interfaces:**

- Produces: 운영 리소스와 같은 변수 입력을 받는 후보 템플릿 여섯 개
- Does not modify: `src/main/resources/prompts/interview/`

- [x] **Step 1: 후보 프롬프트 계약 테스트 작성**

```java
@Test
void questionCandidatesRequireOnePlainQuestionWithoutPraise() {
    assertThat(firstSystem)
            .contains("질문 하나")
            .contains("Markdown")
            .contains("칭찬")
            .contains("역할 접두사");
    assertThat(followUpSystem)
            .contains("마지막 답변")
            .contains("질문 중복")
            .contains("새 주제로 전환");
}

@Test
void feedbackCandidateRequiresGroundedExactQuotesAndTierContract() {
    assertThat(feedbackSystem)
            .contains("지원자의 실제 답변")
            .contains("연속된 원문")
            .contains("questionNumber")
            .contains("Free", "Basic", "Pro");
}
```

- [x] **Step 2: 계약 테스트 실패 확인**

Run: `./gradlew test --tests "app.mockly.domain.interview.evaluation.InterviewPromptCandidateContractTest"`

Expected: 후보 파일 부재로 FAIL

- [x] **Step 3: 첫 질문 후보 작성**

포지션·경력·면접 유형·지정 키워드에 맞는 평가 포인트 하나를 선택하게 한다. 자기소개는 난이도와 주제 참고에만 사용하고 세부 경험을 사실로 전제하지 않게 한다. 복합 질문, 암기형 함정, Markdown, 설명, 평가, 칭찬, 역할 접두사를 금지한다.

- [x] **Step 4: 후속 질문 후보 작성**

마지막 답변을 직접 근거로 삼고 전체 대화는 중복과 주제 포화 판단에 사용한다. 피상적 답변에는 쉬운 구체화 질문, 충분한 답변에는 판단 근거·결정·트레이드오프 질문을 사용하며 주제가 포화됐거나 답변 불가일 때만 전환한다.

- [x] **Step 5: 피드백 후보 작성**

모든 판단을 실제 사용자 답변에 연결한다. 유료 `quote`는 해당 `questionNumber` 답변의 수정되지 않은 연속 원문으로 요구한다. 4축 점수, 브리핑, 강점과 개선점의 일관성을 자체 점검하게 하고 현재 Free/Basic/Pro null·목록 계약을 유지한다.

- [x] **Step 6: 후보 계약 테스트 실행**

Run: `./gradlew test --tests "app.mockly.domain.interview.evaluation.InterviewPromptCandidateContractTest"`

Expected: PASS

- [x] **Step 7: 커밋 체크포인트 제안**

Suggested message: `test: 면접 프롬프트 평가 후보 추가`

### Task 3: 60건 오프라인 평가 데이터 구성

**Files:**

- Create: `evals/interview/schema/evaluation-case.schema.json`
- Create: `evals/interview/datasets/first-question.jsonl`
- Create: `evals/interview/datasets/follow-up-question.jsonl`
- Create: `evals/interview/datasets/feedback.jsonl`
- Test: `src/test/java/app/mockly/domain/interview/evaluation/InterviewEvaluationDatasetTest.java`

**Interfaces:**

- Produces: 각 줄에 `caseId`, `target`, `source`, `input`, `tags`, `mustSatisfy`, `forbiddenErrors`를 가진 UTF-8 JSON 객체
- Allowed `target`: `FIRST_QUESTION`, `FOLLOW_UP_QUESTION`, `FEEDBACK`
- Allowed `source`: `ANONYMIZED`, `SYNTHETIC`

- [x] **Step 1: 데이터 형식·개수·식별자 검사 테스트 작성**

```java
assertDataset("first-question.jsonl", "FIRST_QUESTION", 15);
assertDataset("follow-up-question.jsonl", "FOLLOW_UP_QUESTION", 25);
assertDataset("feedback.jsonl", "FEEDBACK", 20);
```

검사기는 JSON 파싱, 필수 필드, 전역 `caseId` 중복, 빈 평가 기준, 이메일·전화번호·URL·UUID 형태를 검증한다.

- [x] **Step 2: 테스트가 데이터 부재로 실패하는지 확인**

Run: `./gradlew test --tests "app.mockly.domain.interview.evaluation.InterviewEvaluationDatasetTest"`

Expected: JSONL 파일 부재로 FAIL

- [x] **Step 3: 첫 질문 15건 구성**

기존 LangSmith `first-question-input` 15건을 원본 후보로 활용하되 식별 가능한 이름·회사·프로젝트 서술을 일반화한다. Junior/Mid/Senior, 면접 유형, 구체·광범위 키워드와 자기소개 사실 오인 위험을 포함한다.

- [x] **Step 4: 후속 질문 25건 구성**

익명화 실제 사례와 합성 사례를 섞어 피상 답변, 개념 설명, 나열, 경험·수치, 트레이드오프, 모름, 질문 반복 위험, 주제 포화, 자기소개 사실 오인 위험을 포함한다.

- [x] **Step 5: 피드백 20건 구성**

Free/Basic/Pro를 포함하고 답변 근거 부족, 인용 오연결, 점수·서술 모순, 괄호 남용, 과도한 일반화와 실행 불가능한 조언 위험을 포함한다.

- [ ] **Step 6: 자동 검사와 60건 사람 검토**

Run: `./gradlew test --tests "app.mockly.domain.interview.evaluation.InterviewEvaluationDatasetTest"`

Expected: PASS

사람이 실제 이름, 회사·제품·저장소명, 연락처, 계정 ID, 고유 장애·성과 서술이 남지 않았는지 전수 확인한다. 검토가 끝나기 전에는 외부 업로드를 진행하지 않는다.

- [ ] **Step 7: 커밋 체크포인트 제안**

Suggested message: `test: 면접 프롬프트 오프라인 평가 데이터 추가`

### Task 4: LangSmith v2 평가기와 실험 구성

**Files:**

- Create: `docs/review/interview-prompt-evaluation-guide.md`

**Interfaces:**

- Consumes: Task 2 후보 프롬프트와 Task 3의 검토 완료 JSONL
- Produces: 데이터셋 3개, 평가기 6개, baseline/candidate 실험과 비교 결과

- [ ] **Step 1: 외부 업로드 직전 사용자 확인**

업로드할 세 JSONL의 정확한 경로, 총 60건, 전송 필드와 목적지 LangSmith Personal workspace를 알리고 명시적으로 확인받는다.

- [ ] **Step 2: 기존 자산과 분리된 데이터셋 생성**

- `mockly-first-question-eval-v2`
- `mockly-follow-up-question-eval-v2`
- `mockly-feedback-eval-v2`

- [ ] **Step 3: 1~5점 종합 품질 평가기 생성**

- `first_question_quality_v2`
- `follow_up_question_quality_v2`
- `feedback_quality_v2`

모델은 `gpt-5.5`로 고정한다. 대상별 세부 축을 내부 루브릭으로 모두 검토하고 공통 5점 앵커에 따라 종합 점수 하나와 짧은 근거를 반환하게 한다.

- [ ] **Step 4: 중대 오류 평가기 생성**

- `first_question_critical_failure_v2`
- `follow_up_question_critical_failure_v2`
- `feedback_critical_failure_v2`

설계 문서의 대상별 중대 오류에 해당하면 실패로 기록하고 오류 코드를 근거에 남긴다. 종합 점수와 분리해 치명적인 문제가 평균에 가려지지 않게 한다.

- [ ] **Step 5: 동일 생성 조건으로 baseline과 candidate 실행**

baseline은 Task 1 운영 리소스, candidate는 Task 2 평가 리소스를 사용한다. 두 실험의 생성 모델과 설정은 `gpt-5.4-nano`, reasoning effort `none`, temperature `1.0`으로 동일하게 한다. Git commit, 프롬프트 식별자, 생성 모델·Judge 설정을 메타데이터에 기록한다.

- [ ] **Step 6: blinded pairwise와 사람 검토**

프롬프트 이름을 숨기고 A/B 순서를 무작위화한다. candidate 패배 사례, 중대 오류 사례, 점수 차이가 큰 사례를 사람이 모두 검토한다.

- [ ] **Step 7: 승인 기준과 결과 기록**

- 세 대상의 candidate 종합 평균이 각각 4.0/5 이상
- 세 대상 모두 baseline보다 평균 하락 없음
- 중대 오류 0건
- 사람이 지정 사례를 검토해 승인

가이드에는 데이터셋·평가기·실험 이름, 실행일, Git commit, 평균, 오류 수, 사람 검토 결론을 기록한다.

### Task 5: 승인된 후보만 운영 프롬프트로 승격

**Files:**

- Modify: `src/main/resources/prompts/interview/first-question-system.txt`
- Modify: `src/main/resources/prompts/interview/first-question-user.txt`
- Modify: `src/main/resources/prompts/interview/follow-up-question-system.txt`
- Modify: `src/main/resources/prompts/interview/follow-up-question-user.txt`
- Modify: `src/main/resources/prompts/interview/feedback-system.txt`
- Modify: `src/main/resources/prompts/interview/feedback-user.txt`
- Modify: `src/test/java/app/mockly/domain/interview/service/InterviewPromptFactoryTest.java`

**Interfaces:**

- Consumes: Task 4에서 승인한 candidate의 정확한 파일 내용
- Produces: 승인된 후보와 동일한 운영 프롬프트

- [ ] **Step 1: 승인 조건 확인**

Task 4의 네 승인 조건 중 하나라도 실패하면 운영 파일을 변경하지 않는다. 후보를 수정하고 새 LangSmith 실험 버전으로 Task 4를 다시 수행한다.

- [ ] **Step 2: 후보와 운영 프롬프트 일치 테스트 작성**

```java
assertThat(runtimePrompt("first-question-system.txt"))
        .isEqualTo(candidatePrompt("first-question-system.txt"));
```

여섯 파일 모두 같은 방식으로 비교한다.

- [ ] **Step 3: 승인된 후보를 운영 리소스에 반영**

LangSmith에서 다시 작성하지 않고 실험에 사용한 후보 파일 내용을 그대로 운영 리소스에 옮긴다.

- [ ] **Step 4: 전체 회귀 검증**

Run: `./gradlew test`

Run: `./gradlew openapi3`

Run: `git diff --check`

Expected: 모든 테스트 PASS, 기존 질문 SSE·피드백 API REST Docs 생성 성공, 공백 오류 없음

- [ ] **Step 5: 최종 커밋 대상과 메시지 제안**

운영 코드·리소스·테스트·평가 가이드를 커밋 대상으로 제안한다. 개인정보 검토를 통과한 JSONL만 포함하고 `docs/okf/**`와 기존 로컬 리뷰 메모는 제외한다.

Suggested message:

```text
feat: 면접 AI 프롬프트 평가 체계 적용

- 면접 질문과 피드백 프롬프트를 텍스트 리소스로 분리
- LangSmith 오프라인 평가 데이터와 품질 기준 구성
- 평가 승인된 프롬프트를 운영 리소스에 반영
```

---

## Deferred Follow-up: 운영 Validator와 질문 전달 방식

다음 구현 범위에서 별도 설계한다.

- Markdown, 괄호 짝, 질문 한 문장 형식을 운영 코드로 검사할 범위
- 피드백 `quote`가 실제 답변의 연속 원문인지 검사하는 위치
- 형식 오류 시 재생성 횟수와 모델 통신 오류 재시도 정책
- 질문을 계속 SSE로 스트리밍할지, 전체 검증 후 SSE로 보낼지, 동기 JSON API로 바꿀지
- 각 방식의 첫 응답 시간, 모바일 timeout, 중복 요청과 생성 소유권 처리
