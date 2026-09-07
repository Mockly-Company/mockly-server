# 면접 프롬프트 오프라인 평가 가이드

## 1. 목적과 현재 범위

첫 질문, 후속 질문, 구조화 피드백의 기존 프롬프트와 개선 후보를 동일한 입력으로 비교한다. 이번 작업은 오프라인 품질 평가이며 운영 요청을 LangSmith로 전송하지 않는다.

운영 Validator, 질문 재생성, 질문 SSE 변경은 포함하지 않는다. 따라서 이 평가 작업 자체는 운영 API 응답 시간에 영향을 주지 않는다.

## 2. 저장소 자산

### 운영 기준 프롬프트

`src/main/resources/prompts/interview/`의 여섯 파일을 baseline으로 사용한다.

### 개선 후보 프롬프트

`evals/interview/prompts/candidate/`의 여섯 파일을 candidate로 사용한다. 평가 승인 전에는 운영 코드가 이 파일을 읽지 않는다.

candidate는 `gpt-5.4-nano` 기준으로 다음 원칙을 적용한다.

- system 템플릿은 `role`, `objective`, 근거·입력 규칙, 판단 절차, 출력 계약, 검증의 XML 의미 블록으로 구분한다.
- user 템플릿의 자기소개와 대화는 지시가 아닌 데이터 블록으로 격리한다.
- 질문 결과는 일반 텍스트 한 문장으로 제한한다.
- 피드백의 JSON 구조는 프롬프트에 중복 작성하지 않고 provider-native Structured Output에 맡긴다.
- 운영 승격 시에는 동적 문자열의 XML 특수문자 escape를 함께 구현한다.

### 평가 데이터

| 대상 | 파일 | 건수 | 출처 |
|---|---|---:|---|
| 첫 질문 | `evals/interview/datasets/first-question.jsonl` | 15 | 기존 LangSmith 입력을 식별 불가능한 직무 사례로 일반화 |
| 후속 질문 | `evals/interview/datasets/follow-up-question.jsonl` | 25 | 합성 경계 사례 |
| 피드백 | `evals/interview/datasets/feedback.jsonl` | 20 | 합성 경계 사례 |

각 줄은 다음 필드를 가진다.

- `caseId`: 저장소 안에서 중복되지 않는 사례 ID
- `target`: `FIRST_QUESTION`, `FOLLOW_UP_QUESTION`, `FEEDBACK`
- `source`: `ANONYMIZED`, `SYNTHETIC`
- `input`: 생성 프롬프트에 전달할 입력
- `tags`: 실패 유형과 사례 분류
- `mustSatisfy`: 결과가 반드시 충족해야 할 조건
- `forbiddenErrors`: 결과에 나타나면 안 되는 오류

## 3. 업로드 전 개인정보 검토

자동 테스트는 이메일, 국내 휴대전화 번호, URL, UUID 형태와 필수 필드·개수를 검사한다. 자동 검사는 고유 프로젝트 설명이나 간접 식별 가능성을 완전히 판정할 수 없으므로 아래 항목은 사람이 60건 모두 확인한다.

- 실제 이름이나 닉네임
- 회사·제품·프로젝트·저장소 이름
- 연락처, 계정 ID, 외부 링크
- 특정 개인이나 조직을 식별할 수 있는 장애 내용
- 고유한 매출, 트래픽, 성과 수치

검토가 끝나기 전에는 JSONL을 LangSmith에 업로드하지 않는다.

## 4. LangSmith 데이터셋

기존 데이터셋은 수정하지 않고 다음 데이터셋을 새로 만든다.

- `mockly-first-question-eval-v2`
- `mockly-follow-up-question-eval-v2`
- `mockly-feedback-eval-v2`

입력에는 JSONL의 `input`, 평가 기준에는 `mustSatisfy`, `forbiddenErrors`, `tags`, `caseId`를 함께 넣는다. 정답 문장 하나를 reference output으로 강제하지 않는다.

## 5. 공통 Judge 설정

- 평가 방식: LLM-as-a-Judge
- Judge 모델: `gpt-5.5`
- baseline과 candidate에 동일한 모델 버전과 설정 사용
- 품질 feedback: 정수 1~5, reasoning 포함
- 중대 오류 feedback: 0 또는 1, reasoning에 오류 코드 포함

`gpt-5.5`를 선택할 수 없으면 다른 모델로 임의 대체하지 않고 실험을 중단한다.

### 5점 앵커

| 점수 | 의미 |
|---:|---|
| 1 | 사용할 수 없거나 중대 오류가 있음 |
| 2 | 여러 핵심 기준을 위반함 |
| 3 | 사용할 수 있지만 명확한 수정이 필요함 |
| 4 | 운영에 사용할 수 있고 사소한 개선만 필요함 |
| 5 | 모든 핵심 기준을 충족하는 우수한 결과 |

## 6. 품질 평가기

### `first_question_quality_v2`

내부 기준은 `relevance`, `singleFocus`, `clarity`, `naturalness`, `answerability`, `keywordAlignment`다. 별도 축 점수를 만들지 않고 모든 기준을 검토한 종합 점수 하나를 반환한다.

Evaluator system prompt:

```text
당신은 모의 면접의 첫 질문 품질을 평가합니다. input의 지원 직무, 경력 수준, 면접 유형, 탐색 키워드, mustSatisfy, forbiddenErrors와 output의 질문을 비교하세요.

다음을 내부적으로 모두 검토하세요.
- 직무·경력·면접 유형에 적합한가
- 탐색 키워드와 직접 관련되는가
- 하나의 평가 포인트에 집중하는가
- 실제 면접관의 질문처럼 자연스러운가
- 요구하는 답변 범위가 명확하고 답변 가능한가
- 자기소개를 참고하되 확인되지 않은 세부 경험을 사실로 전제하지 않는가

1~5점 앵커를 엄격히 적용하세요. 세부 축별 점수는 출력하지 마세요. reasoning은 가장 중요한 강점과 약점을 한국어로 짧게 설명하세요.
```

### `follow_up_question_quality_v2`

내부 기준은 `relevance`, `singleFocus`, `clarity`, `naturalness`, `answerability`, `contextGrounding`, `progression`, `transitionQuality`다.

Evaluator system prompt:

```text
당신은 모의 면접의 후속 질문 품질을 평가합니다. input의 전체 대화, mustSatisfy, forbiddenErrors와 output의 질문을 비교하세요.

다음을 내부적으로 모두 검토하세요.
- 직전 지원자 답변을 직접 근거로 하는가
- 이미 확인한 질문을 반복하지 않고 대화를 진전시키는가
- 피상적 답변에는 구체화를, 충분한 답변에는 판단 근거나 트레이드오프를 묻는가
- 주제가 충분히 탐색됐거나 답변 불가일 때만 자연스럽게 전환하는가
- 하나의 평가 포인트에 집중하고 명확하며 자연스러운가
- 지원자가 말하지 않은 사실을 전제하지 않는가

1~5점 앵커를 엄격히 적용하세요. 세부 축별 점수는 출력하지 마세요. reasoning은 가장 중요한 강점과 약점을 한국어로 짧게 설명하세요.
```

### `feedback_quality_v2`

내부 기준은 `groundedness`, `specificity`, `actionability`, `scoreConsistency`, `evidenceAlignment`, `readability`, `tierFitness`다.

Evaluator system prompt:

```text
당신은 모의 면접 구조화 피드백의 품질을 평가합니다. input의 면접 대화와 플랜 티어, mustSatisfy, forbiddenErrors를 output과 비교하세요.

다음을 내부적으로 모두 검토하세요.
- 모든 판단이 실제 지원자 답변에 근거하는가
- 강점과 개선점이 해당 지원자에게 구체적인가
- 개선 조언을 다음 답변에서 실행할 수 있는가
- 4축 점수, 종합 점수, 코치 브리핑, 강점과 개선점이 일관되는가
- questionNumber와 quote가 해당 답변의 의미와 일치하는가
- 불필요한 괄호와 장황한 표현 없이 읽기 쉬운가
- Free, Basic, Pro의 정보량과 null·목록 계약에 맞는가

1~5점 앵커를 엄격히 적용하세요. 세부 축별 점수는 출력하지 마세요. reasoning은 가장 중요한 강점과 약점을 한국어로 짧게 설명하세요.
```

각 품질 평가기의 user prompt는 다음 형식을 사용한다.

```text
다음 사례를 평가하세요.

<input>
{{input}}
</input>

<output>
{{output}}
</output>
```

## 7. 중대 오류 평가기

### `first_question_critical_failure_v2`

오류 코드:

- `MULTIPLE_QUESTIONS`
- `NOT_A_QUESTION`
- `UNSUPPORTED_ASSUMPTION`
- `ROLE_PREFIX_OR_PRAISE`

### `follow_up_question_critical_failure_v2`

오류 코드:

- `MULTIPLE_QUESTIONS`
- `NOT_A_QUESTION`
- `UNSUPPORTED_ASSUMPTION`
- `REPEATED_QUESTION`
- `ROLE_PREFIX_OR_PRAISE`

### `feedback_critical_failure_v2`

오류 코드:

- `FABRICATED_CLAIM`
- `FABRICATED_OR_MISLINKED_QUOTE`
- `UNSUPPORTED_PERSONAL_JUDGMENT`
- `CONTRADICTORY_EVALUATION`
- `DISCRIMINATORY_OR_IRRELEVANT_CRITERIA`

공통 Evaluator system prompt:

```text
당신은 결과에 중대 오류가 있는지만 판정합니다. 대상별 오류 코드 목록과 input의 forbiddenErrors를 기준으로 output을 검사하세요.

중대 오류가 하나라도 있으면 score 1, 없으면 score 0을 반환하세요. reasoning에는 발견한 오류 코드를 먼저 쓰고 근거를 한국어로 짧게 설명하세요. 단순한 문체 선호나 사소한 개선점은 중대 오류로 판정하지 마세요.
```

## 8. 실험 조건

- 생성 모델: `gpt-5.4-nano`
- reasoning effort: `none`
- temperature: `1.0`
- baseline과 candidate의 입력 데이터와 생성 설정 동일
- 메타데이터: Git commit, 프롬프트 구분, 생성 모델·설정, Judge 모델·설정
- 비교 화면에서는 프롬프트 이름을 숨기고 A/B 순서를 무작위화

## 9. 승인 기준

- 첫 질문, 후속 질문, 피드백 candidate 평균이 각각 `4.0/5` 이상
- 세 대상 모두 baseline보다 평균이 하락하지 않음
- 중대 오류 0건
- candidate 패배, 중대 오류, 큰 점수 차이 사례를 사람이 전수 검토해 승인

하나라도 충족하지 못하면 운영 프롬프트를 바꾸지 않는다. candidate를 수정하고 새 실험으로 다시 평가한다.

## 10. 결과 기록

실험 후 아래 값을 이 문서의 별도 실행 기록 섹션에 추가한다.

- 실행 날짜와 Git commit
- 데이터셋·평가기·실험 이름
- 대상별 baseline/candidate 평균
- 중대 오류 건수와 오류 코드
- pairwise 승·무·패 수
- 사람이 검토한 caseId와 결론
- 운영 승격 여부

## 11. 모델별 프롬프트 구조 근거

GPT-5.4 공식 가이드는 명시적인 계약을 가진 블록 구조에서 높은 지시 준수 성능을 보인다고 설명하고, `output_contract`, `grounding_rules`, `verification_loop` 같은 XML 의미 블록을 권장 패턴으로 제시한다. 또한 기본 reasoning effort인 `none`에서는 판단 및 점검 절차를 프롬프트에 명확히 제시하는 것이 중요하다.

이 가이드는 특정 포맷이 모든 과업에서 우월하다고 보장하지 않는다. 따라서 XML candidate를 바로 운영에 반영하지 않고 동일 데이터와 동일 생성 설정으로 baseline과 비교한다.

- [GPT-5.4 공식 프롬프팅 가이드](https://developers.openai.com/api/docs/guides/latest-model?model=gpt-5.4)
- [GPT-5.4 nano 공식 모델 페이지](https://developers.openai.com/api/docs/models/gpt-5.4-nano)
