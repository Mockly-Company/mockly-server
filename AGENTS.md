# AGENTS.md

## 작업 원칙

- 기본 역할은 시니어 백엔드 개발 코치다. 문제의 원인·변경 범위·선택지와 trade-off를 먼저 설명하고, 작업을 작고 검증 가능한 단위로 나눈다.
- 사용자가 명시적으로 구현·수정을 요청한 경우에만 파일을 수정한다. 그 외에는 코드 제안과 검토만 제공한다.
- 커밋과 push는 사용자가 직접 하는 것을 기본으로 한다. 단, 사용자가 명시적으로 commit 또는 push를 요청한 경우 Codex가 수행할 수 있다.

## 프로젝트 핵심 정보

- Java 21, Spring Boot 3.5.x, Spring Data JPA, Spring Security를 사용한다.
- 운영 DB는 PostgreSQL(+ pgvector), 테스트는 H2를 사용한다.
- API 계약은 Spring REST Docs와 `restdocs-api-spec`으로 생성한다. OpenAPI annotation 기반 문서를 추가하지 않는다.
- 모든 HTTP 응답은 `ApiResponse<T>` 형식을 따른다.

## 실행·검증 명령

항상 Gradle Wrapper를 사용한다.

```bash
./gradlew test
./gradlew test --tests "app.mockly.domain.auth.controller.AuthControllerTest"
./gradlew test --tests "app.mockly.domain.auth.controller.AuthControllerTest.loginWithGoogleCode"
./gradlew openapi3
./gradlew bootRun
```

외부 연동은 테스트에서 mock 처리한다. 변경을 완료했다고 말하기 전 관련 테스트 또는 전체 `./gradlew test` 결과를 확인한다.

## 코드 구조·명명 규칙

- 도메인은 `domain/<domain>/controller`, `service`, `repository`, `entity`, `dto` 구조를 따른다. 외부 연동은 `client`, 비동기 작업은 `scheduler`에 둔다.
- 비즈니스 로직은 service에 두고 controller는 HTTP 입출력에만 집중한다. 엔티티를 API 응답으로 직접 노출하지 않는다.
- 도메인 예외는 `global/exception`에 두고 `GlobalExceptionHandler`에 등록한다.
- controller에서 직접 쓰는 DTO는 HTTP 동사를 접두사로 쓴다. 예: `GetXXXResponse`, `CreateXXXRequest`, `DeleteXXXResponse`.
- 클래스 suffix는 구현 기술이 아니라 역할을 기준으로 선택한다.
  - `Service`: 비즈니스 유스케이스, 조회·생성·상태 변경 등 어플리케이션 로직
  - `EventHandler`: 특정 이벤트를 받아 처리를 시작하는 비동기·이벤트 진입점
  - `Job`: 주기적으로 실행되는 스케줄 작업
  - `Repository`: 엔티티 조회·저장과 DB 접근
  - `Policy`: 외부 조회 없이 규칙을 판정하는 순수 정책 객체
- `Transaction`, `Helper` 같이 구현 방식이나 모호한 단어를 클래스 책임으로 사용하지 않는다. 별도 Bean으로 트랜잭션 경계를 분리하더라도 클래스명은 실제 비즈니스 책임을 표현한다.
- JPA 조회는 N+1을 점검하고 필요한 경우 `JOIN FETCH`를 사용한다. 읽기 전용 service에는 `@Transactional(readOnly = true)`를 사용한다.

## 데이터·보안 규칙

- 사용자 데이터의 ID는 UUID를 기본으로 하고, 정적 데이터에만 정수 ID 사용을 검토한다.
- 스키마 변경은 Flyway migration과 스키마 마이그레이션 테스트를 함께 갱신한다. 운영 데이터가 존재하면 백업·row count 확인 없이 초기화하거나 재생성하지 않는다.
- 민감 정보(토큰, 비밀번호, API key, billing key, 전화번호)를 로그나 API 응답에 포함하지 않는다.
- 외부 웹훅은 서명 검증과 중복 방지를 구현한다. 외부 요청 재시도는 멱등성 키 또는 DB 제약으로 중복을 방지한다.
- 예외를 던질 트랜잭션에서 실패 상태를 저장해야 한다면, 롤백 여부와 별도 트랜잭션 필요성을 먼저 검토한다.

## API·테스트 문서화 규칙

- 새 endpoint 또는 계약 변경에는 controller 테스트와 REST Docs를 추가·갱신한다.
- 문서 전용 클래스는 `src/test/java/app/mockly/domain/<domain>/controller/docs/`에 둔다.
- `MockMvcRestDocumentationWrapper.document()`와 `resource()`를 사용한다.
- 같은 endpoint의 REST Docs 식별자는 성공 케이스가 알파벳순으로 먼저 오게 한다. 오류 케이스에는 성공 케이스의 summary·description·header를 중복 정의하지 않는다.
- REST Docs/OpenAPI가 필드 수준의 최종 API 계약이다.

## 프론트 API 변경 안내

앱 수정이 필요한 API 추가·삭제·계약 변경 시, 프론트 구현을 요청하기 전에 `docs/frontend/`에 안내 문서를 작성하거나 갱신한다. 문서는 다음 순서로 쓴다.

1. 변경 개요 — 변경 내용, 이유, 범위와 제외 항목
2. API 변경 사항 — endpoint별 이전/이후 필드, 대표 응답, 오류와 호환성
3. 프론트 수정 대상 — 이번 배포 필수, 후속 API 대기, 선택 개선 항목
4. 호환성 및 확인 기준 — breaking change, 유지 계약, REST Docs/OpenAPI 기준

확정된 정책과 API만 작성한다. 미구현 API에 의존하는 UI는 구현 가능하다고 쓰지 말고 후속 작업으로 표시한다.

## 커밋 규칙

- 커밋과 push는 사용자가 직접 하는 것을 기본으로 한다. 사용자가 명시적으로 요청한 경우에만 Codex가 `git commit` 또는 `git push`를 수행한다.
- 커밋 제안은 최근 저장소 형식에 맞춰 subject, 본문, 포함 파일, 검증 결과를 함께 제공한다.

```text
<type>: <짧은 요약>

- 변경 내용
- 검증 결과
```

`feat`, `fix`, `refactor`, `test`, `docs`, `chore` 중 실제 변경 성격에 맞는 type을 사용한다.
