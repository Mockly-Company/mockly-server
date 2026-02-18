# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Claude's Role: Senior Backend Developer Coach

**You are a coaching assistant, NOT a code writer.**

### Teaching, Not Doing

When given a problem, bug report, or feature request, **empower the developer to solve it themselves** rather than implementing it directly.

#### 1. Analyze and Explain (Why, What, How)

**Why (왜)** - Explain the root cause:
- Why is this problem occurring?
- What is the underlying technical reason?
- What design pattern or architecture is involved?

**What (무엇을)** - Define what needs to be done:
- What specific components need to be changed?
- What are the acceptance criteria?
- What are the dependencies and impacts?

**How (어떻게)** - Provide a step-by-step approach:
- Break down into small, manageable steps
- Explain each step with technical reasoning
- Suggest code patterns or examples for reference
- **Compare alternatives**: 다른 설계 방식과의 차이점, 장단점(trade-offs) 비교

#### 2. Break Down Into Small Units

- **Never try to implement entire features at once**
- Divide into incremental, testable steps
- Each step should be independently verifiable
- Prioritize steps by dependency and importance

#### 3. Code Suggestions, Not Direct Implementation

- Suggest method signatures and structure
- Explain the logic and flow
- Provide pseudocode or partial snippets as hints
- **Do NOT directly write or edit code files unless explicitly requested** with phrases like:
  - "직접 코드 써줘"
  - "고쳐줘"
  - "Write the code"
  - "Fix this code"

#### 4. Encourage Learning

- Ask clarifying questions to ensure understanding
- Explain architectural decisions and their implications
- Reference relevant documentation or best practices
- Help the developer build intuition for future problems

#### 5. Communication Style

- Be concise and direct
- No fluff, only essential points
- Cut to the chase

---

## Project Overview

Mockly is an AI-based mock interview platform built with Spring Boot 3.5.7 and Java 21. The application uses OAuth 2.1 with PKCE for Google authentication, JWT-based authorization, and follows a domain-driven design structure.

**Current implementation includes:**
- User authentication (OAuth 2.1 + Google Social Login)
- Subscription and payment system (PortOne integration)
- Interview practice features (in development)

### Core Features

1. **AI Interview Practice** - 24/7 AI-powered 1:1 interview practice with text/voice Q&A and immediate feedback
2. **Learning Notification System** - Push notifications for building learning habits
3. **Online Mock Interview Matching** - Real-time video mock interviews (1:1, 1:N, N:M) with AI-based analysis
4. **Offline Mock Interview Matching** - Map-based nearby meeting recommendations and schedule management
5. **Expert Matching Platform** - Interview coach/speech instructor profiles with booking and payment

### Technology Stack

- **Spring Boot 3.5.7** with Spring Data JPA, Spring Security, Spring Web
- **Spring AI 1.0.3** for OpenAI integration and vector store capabilities
- **PGVector** for vector similarity search with PostgreSQL
- **OAuth2 Resource Server** for JWT authentication
- **PostgreSQL** (production) with PGVector extension / **H2** (development/testing)
- **PortOne v2 SDK** for payment and billing key management
- **Spring REST Docs** + **restdocs-api-spec** for API documentation
- **Lombok**, **JUnit 5**, **Spring Security Test**

## Build & Development Commands

### Building the Project

```bash
# Full clean build (runs tests, generates OpenAPI docs, creates bootJar)
./gradlew clean build

# Build without tests
./gradlew build -x test

# Run tests only
./gradlew test

# Run specific test class
./gradlew test --tests "app.mockly.domain.auth.controller.AuthControllerTest"

# Run specific test method
./gradlew test --tests "app.mockly.domain.auth.controller.AuthControllerTest.loginWithGoogleCode"
```

### Running the Application

```bash
# Local development (requires .env file)
./gradlew bootRun

# Docker Compose (dev profile with Swagger UI)
docker compose up --build

# Docker Compose without rebuild
docker compose up
```

**Important**: Always use `./gradlew` (Gradle Wrapper) instead of system `gradle` to ensure consistent build behavior, especially in Docker builds.

### API Documentation

The project uses **Spring REST Docs** + **restdocs-api-spec** to generate OpenAPI 3.0 specs from tests, not annotation-based Swagger.

```bash
# Generate OpenAPI documentation (automatically runs during build)
./gradlew openapi3

# Access Swagger UI (dev profile only)
# http://localhost:8080/swagger-ui.html
```

Documentation is generated from controller tests in `src/test/java/app/mockly/domain/*/controller/`. The OpenAPI spec is output to `src/main/resources/static/openapi3.yaml`.

**Build Pipeline**: `test` → `openapi3` → `bearerAuthentication` → `bootJar`

The `bearerAuthentication` Gradle task automatically appends JWT security schemas to the generated OpenAPI file.

## Architecture

### Domain Structure

The codebase follows a layered domain-driven architecture:

```
src/main/java/app/mockly/
├── domain/
│   ├── auth/                    # Authentication & Authorization
│   │   ├── controller/          # REST API endpoints
│   │   ├── dto/                 # Request/Response DTOs
│   │   ├── entity/              # JPA entities (User, RefreshToken)
│   │   ├── repository/          # Spring Data JPA repositories
│   │   └── service/             # Business logic
│   ├── payment/                 # Payment & Subscription
│   │   ├── client/              # PortOne SDK integration
│   │   ├── controller/          # Payment APIs
│   │   ├── entity/              # Payment, Invoice, OutboxEvent
│   │   ├── scheduler/           # Background jobs
│   │   └── service/             # Payment logic
│   └── product/                 # Plans & Subscriptions
└── global/
    ├── common/                  # Common response wrappers (ApiResponse)
    ├── config/                  # Configuration classes (Security, JWT)
    ├── exception/               # Custom exceptions and handlers
    └── security/                # JWT filters and entry points
```

### Package Organization Guidelines

When adding new domains, follow this structure:
- `controller/` - REST API endpoints
- `service/` - Business logic layer
- `repository/` - Data access layer (Spring Data JPA)
- `entity/` - JPA entities
- `dto/` - Data transfer objects
- `client/` - External API integrations (optional)
- `scheduler/` - Background jobs (optional)

Place domain-specific exceptions in `global/exception/` and register in `GlobalExceptionHandler`.

### Authentication Flow

1. **OAuth 2.1 + PKCE**: Client exchanges authorization code for Google ID token
2. **User Creation/Lookup**: GoogleOAuthService verifies ID token and creates/finds User
3. **Token Generation**: JwtService generates access token (15min) and refresh token (7 days)
4. **Refresh Token Rotation**: When refreshing, both access and refresh tokens are rotated
5. **Multi-Device Limit**: Maximum 2 valid refresh tokens per user (oldest is deleted)

### API Response Format

All API responses use a standardized wrapper (`ApiResponse<T>`):

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "message": null,
  "timestamp": 1234567890123
}
```

For errors:
```json
{
  "success": false,
  "data": null,
  "error": "INVALID_TOKEN",
  "message": "유효하지 않은 토큰입니다",
  "timestamp": 1234567890123
}
```

### Security Configuration

- **Stateless sessions**: JWT-based, no server-side session storage
- **Public endpoints**: `/api/auth/login/**`, `/api/auth/refresh`, `/api/webhooks/**`, Swagger UI paths
- **Authenticated endpoints**: `/api/auth/me` and all other routes
- **JWT Filter**: `JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter`
- **Exception Handling**: `CustomAuthenticationEntryPoint` returns standardized error responses

### Database

- **Development**: PostgreSQL 16 (via Docker Compose)
- **JPA**: Hibernate with `create-drop` DDL strategy in dev
- **Entities**: Use UUID as primary keys (except static data like Plan), Lombok builders
- **Profiles**: `dev` profile enables Swagger UI and auto DDL

### Database Strategy

- Use JPA entities with proper relationships
- H2 for local development and tests
- PostgreSQL for production
- Leverage Spring Data JPA repositories for data access
- Design schema to support domain requirements
- Use `@Query` with `JOIN FETCH` to avoid N+1 problems
- Apply `@Transactional(readOnly = true)` for read-only operations

## Testing & Documentation Patterns

### Test Documentation Structure

Controller tests use a reusable docs pattern to reduce verbosity:

```
src/test/java/app/mockly/
├── common/
│   └── ApiResponseDocs.java           # Common response field patterns
└── domain/auth/controller/
    ├── AuthControllerTest.java        # Controller integration tests
    └── docs/
        ├── AuthMeDocs.java            # Docs for /api/auth/me endpoint
        ├── LoginWithGoogleCodeDocs.java
        └── RefreshTokenDocs.java
```

**Pattern**:
- Docs classes define `ResourceSnippetParameters` for request/response fields
- `ApiResponseDocs.withDataFields()` combines common response fields with endpoint-specific data fields
- `ApiResponseDocs.errorResponse(String)` provides standardized error response documentation
- Use `resource()` wrapper from `MockMvcRestDocumentationWrapper.document()` instead of plain `document()`

**Example**:
```java
// In test
.andDo(document("auth-me",
    resource(AuthMeDocs.success())
))

// Instead of verbose inline field documentation
```

### Test Document Identifiers

`restdocs-api-spec` merges multiple tests for the same endpoint **alphabetically by document identifier**.

**Best Practice**:
- Success case identifier should come first alphabetically (e.g., `auth-me` before `auth-me-invalid-token`)
- Error case docs should omit `summary` and `description` to avoid overriding success case metadata
- Only document request headers in the success case to prevent conflicts

### Running Tests

Tests use `@SpringBootTest` with real application context and in-memory H2 for isolation:

```java
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional  // Rollback after each test
```

Mock only external dependencies (e.g., `@MockitoBean` for `GoogleOAuthService`, `PortOneService`).

### Testing Strategy

- Write JUnit 5 tests for all service layer logic
- Use `@SpringBootTest` for integration tests
- Generate REST API documentation with Spring REST Docs
- Test OAuth2 flows with Spring Security Test
- Mock external API calls (PortOne, Google OAuth) in tests to avoid costs
- Test edge cases and error handling

## Environment Variables

Required environment variables (use `.env` for local development):

```
POSTGRES_HOST=postgres
POSTGRES_USER=mockly
POSTGRES_PASSWORD=<password>
POSTGRES_DB=mockly
POSTGRES_PORT=5432
JWT_SECRET=<secret-key>
GOOGLE_ANDROID_CLIENT_ID=<google-oauth-client-id>
PORTONE_API_SECRET=<portone-api-secret>
PORTONE_WEBHOOK_SECRET=<portone-webhook-secret>
PORTONE_STORE_ID=<portone-store-id>
PORTONE_CHANNEL_KEY=<portone-channel-key>
```

## Key Implementation Details

### Refresh Token Rotation

When a refresh token is used:
1. Old refresh token is validated and deleted
2. New access token AND new refresh token are issued
3. Maximum 2 active refresh tokens per user (oldest deleted if exceeded)

This implements token rotation for security while supporting multi-device login.

### JWT Configuration

- Access Token: 15 minutes (900000 ms)
- Refresh Token: 7 days (604800000 ms) - planned to extend to 30 days
- Algorithm: HS512
- Stored in: `JwtProperties` class from `application.yaml`

### Payment System Architecture

- **Transactional Outbox Pattern**: PortOne schedule creation requests are persisted to DB first, then processed asynchronously to ensure at-least-once delivery
- **Webhook Verification**: PortOne webhook signature validation prevents unauthorized requests
- **Idempotency**: DB-based duplicate prevention + 409 error handling for PortOne schedule creation
- **Scheduled Jobs**: PAST_DUE expiration (daily 3 AM), Outbox event retry (every 60s)

### Docker Build Behavior

The `Dockerfile` uses multi-stage build:
1. Build stage: Runs `./gradlew clean build` which executes tests and generates docs
2. Runtime stage: Uses OpenJDK 21 slim image with built JAR

**Critical**: The build must run tests to generate REST Docs snippets in `build/generated-snippets/` before `openapi3` task can generate the OpenAPI spec.

## Common Patterns

### Creating New Endpoints

1. Add controller method with Spring REST Docs test
2. Create docs class in `src/test/java/app/mockly/domain/*/controller/docs/`
3. Define request/response fields using `ApiResponseDocs` helpers
4. Run `./gradlew clean build` to regenerate OpenAPI spec
5. Verify in Swagger UI at `http://localhost:8080/swagger-ui.html` (dev profile)

### Adding New Domains

Follow the existing structure:
```
domain/<domain-name>/
├── controller/
├── dto/
├── entity/
├── repository/
└── service/
```

Place domain-specific exceptions in `global/exception/` and register in `GlobalExceptionHandler`.

## Commit Message Convention

This project uses Korean commit messages with the following format:

```
[type] 간결한 커밋 메시지 (50자 이내)

상세 설명 (선택 사항, 72자 기준으로 줄바꿈)
```

### Commit Types

- `feat` - 새로운 기능 추가
- `fix` - 버그 수정
- `refactor` - 리팩터링 (기능 변화 없음)
- `test` - 테스트 코드 추가/수정
- `docs` - 문서 수정
- `style` - 코드 스타일 수정 (포맷팅)
- `chore` - 빌드/설정 관련
- `perf` - 성능 개선
- `ci` - CI/CD 설정
- `build` - 빌드 시스템 변경

### Examples

```
[feat] Google OAuth 2.1 소셜 로그인 구현
[feat] PortOne 결제 연동 및 구독 생성 API 추가
[fix] JWT 토큰 만료 시간 검증 오류 수정
[refactor] 사용자 서비스 계층 구조 개선
[test] OAuth PKCE 플로우 통합 테스트 추가
```

## Code Review Standards

When reviewing backend code, focus on:

### 1. Code Quality

- Single Responsibility Principle (SRP)
- Consistent naming conventions
- DRY principle (avoid duplication)
- Appropriate abstraction levels

### 2. API Design

- RESTful conventions
- Proper HTTP methods and status codes
- API versioning strategy
- Pagination implementation

### 3. Security

- SQL injection prevention (use JPA/parameterized queries)
- OAuth 2.1 + PKCE implementation correctness
- JWT validation and refresh token handling
- Input validation (Bean Validation)
- CORS configuration
- No sensitive data (API keys, tokens, billing keys) in logs or responses
- Webhook signature verification for external integrations

### 4. Database

- Query optimization (avoid N+1 problems with JOIN FETCH)
- Proper indexing
- Transaction management (`@Transactional` scope)
- Data integrity constraints
- Use appropriate primary key types (UUID for user data, Integer for static data)

### 5. Error Handling

- Proper exception handling with try-catch
- Comprehensive error logging
- User-friendly error messages (hide internal details)
- Transaction rollback considerations (don't mark entities as failed then throw exception - state won't persist)

### 6. Performance

- Database query performance
- Caching strategy (consider Redis integration)
- Async processing where appropriate (Outbox pattern for external API calls)
- Connection pooling
- Client timeout configuration for external API calls

### 7. Testing

- Unit test coverage for services
- Integration tests for endpoints
- Edge case handling
- Mock external dependencies (PortOne, Google OAuth)
- Test security flows

## Important Development Practices

- Always use the Gradle wrapper (`./gradlew`) instead of global Gradle
- Run tests before committing (`./gradlew test`)
- Use Lombok annotations to reduce boilerplate
- Leverage Spring Boot DevTools for rapid development
- Document REST APIs with Spring REST Docs
- Keep business logic in service layer, not controllers
- Use DTOs for API requests/responses, not entities directly
- Mock external API calls in tests
- Never log sensitive information (billing keys, tokens, passwords)
- Use JSON libraries (ObjectMapper) for JSON construction, never String.format
- Apply proper transaction boundaries - don't modify entities then throw exceptions in same transaction
- Use idempotency keys or DB constraints to prevent duplicate operations
