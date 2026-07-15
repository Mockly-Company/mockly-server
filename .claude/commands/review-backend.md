현재 git 변경사항을 **Java 백엔드(Spring Boot) 관점**에서 검토하고 다음 항목들을 확인해주세요:

## 검토 항목

### 1. 코드 품질 / 계층 설계
- 단일 책임 원칙(SRP), Java 네이밍 컨벤션
- 계층 분리 준수: Controller(요청/응답) · Service(비즈니스) · Repository(영속성)
- Controller에 비즈니스 로직이 새어들지 않았는지
- Lombok 남용 여부(@Data 무분별 사용, 불변성 훼손)
- 매직 넘버/문자열 상수화

### 2. API 설계
- RESTful 규칙, 올바른 HTTP 메서드/상태 코드(200/201/204/400/401/403/404/409/500)
- **엔티티를 직접 노출하지 않고 요청/응답 DTO 사용**
- Bean Validation(@Valid, @NotNull, @Size 등) 적용
- 페이지네이션(Pageable/Slice) 및 정렬 처리
- 전역 예외 처리(@RestControllerAdvice)로 일관된 에러 응답

### 3. JPA / 데이터베이스 (가장 중요)
- **N+1 문제**: fetch join / @EntityGraph / @BatchSize 적용 여부
- **@Transactional 경계**: 서비스 계층 적용, 조회는 readOnly=true, 전파/롤백 규칙
- 지연 로딩(LAZY)과 LazyInitializationException 위험
- 연관관계 매핑 적절성(단방향 우선, mappedBy, cascade/orphanRemoval 주의)
- 영속성 컨텍스트/변경 감지(dirty checking) 이해, 불필요한 flush
- 인덱스·유니크 제약, DDL 마이그레이션(Flyway/Liquibase) 전략
- QueryDSL/JPQL의 파라미터 바인딩

### 4. 보안
- **SQL Injection**: JPQL/네이티브 쿼리 파라미터 바인딩(문자열 연결 금지)
- **인증/인가**: Spring Security 설정, @PreAuthorize/메서드 보안, 리소스 소유자 검증
- 민감정보: 비밀번호 BCrypt 해싱, 시크릿은 환경변수/외부설정(하드코딩 금지)
- 민감정보 로깅/응답 노출 금지
- CORS·CSRF 정책 적절성

### 5. 예외 처리 / 로깅
- 커스텀 예외 + @ExceptionHandler로 의미 있는 에러 응답
- 예외 삼키기(빈 catch) 금지, 원인 예외 체이닝 유지
- SLF4J 로깅(적절한 레벨), 스택트레이스/컨텍스트, 내부 구현 노출 방지

### 6. 성능 / 동시성
- 캐싱(@Cacheable/Redis) 및 무효화 전략
- 커넥션 풀(HikariCP) 설정, 트랜잭션 범위 최소화
- 비동기·배치(@Async, 메시지 큐)의 적절성
- 동시성 제어: 낙관적/비관적 락, 멱등성 보장

### 7. 테스트
- JUnit5 + Mockito 단위 테스트, 슬라이스 테스트(@WebMvcTest, @DataJpaTest)
- 통합 테스트(@SpringBootTest), 주요 비즈니스 경로/엣지 케이스 커버
- 테스트 격리(고정 데이터/트랜잭션 롤백)

각 항목에 대해 구체적인 피드백을 제공하고, 개선이 필요한 부분은 코드 예시와 함께 제안해주세요.

