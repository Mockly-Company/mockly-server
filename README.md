# Mockly

> Mockly는 AI 면접관과 1:1 모의 면접을 연습할 수 있는 구독형 모바일 플랫폼입니다.

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.5.7-6DB33F?style=flat&logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring_AI_1.0.3-6DB33F?style=flat&logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL_16-4169E1?style=flat&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis_7-FF4438?style=flat&logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white)

---

## 주요 기능

### OAuth 2.1 PKCE 기반 Google 소셜 로그인

모바일 환경에서 `client_secret`을 클라이언트에 두지 않는 대신, PKCE(Proof Key for Code Exchange)를 적용하여 인가 코드 탈취 공격을 방어합니다.

Refresh Token Rotation과 Redis 블랙리스트를 조합해 토큰 탈취에도 즉시 대응할 수 있는 구조를 설계하였습니다.

### PortOne v2 연동 구독 결제 시스템

결제 직후 외부 API 호출(스케줄 등록) 실패 시 데이터 불일치가 생기지 않도록 **Transactional Outbox Pattern**을 도입하였습니다.

중복 결제 방지를 위해 (1) 구독 생성 전 DB 상태 조회, (2) Outbox 재처리 시 `scheduleId` 존재 여부 확인, (3) PortOne API의 `paymentId` 기반 멱등성 체크 등을 수행하였습니다.

### Spring AI 기반 AI 모의 면접 (개발중)

면접 유형별로 평가 카테고리를 다르게 적용하여 맥락에 맞는 피드백을 생성합니다.

`BeanOutputConverter`로 AI 응답 형식을 클래스로 강제하여 JSON 파싱 오류를 줄였습니다.

### 테스트 기반 API 문서 자동화

Spring REST Docs를 사용해 **테스트가 통과해야만 문서가 생성**되는 구조를 채택했습니다.<br/>
API 스펙과 실제 동작 사이의 불일치를 빌드 단계에서 방지합니다.

---

## 기술 스택

| 분류 | 기술                                                           |
|---|--------------------------------------------------------------|
| Backend | Spring Boot 3.5.7, Java 21, Spring AI 1.0.3, Spring Security |
| Database | PostgreSQL 16, PGVector, Redis 7                             |
| External | Google OAuth 2.1, PortOne v2, OpenAI GPT-4o                  |
| Infra/DevOps | Docker, Docker Compose, GitHub Actions                       |
| Docs | Spring REST Docs, OpenAPI 3.0                                |

---

## 프로젝트 구조

```
src/main/java/app/mockly/
├── domain/
│   ├── auth/         # OAuth 2.1 + JWT 인증
│   ├── payment/      # 결제 처리 (Outbox Pattern)
│   ├── product/      # 구독 상품
│   └── interview/    # AI 모의 면접 (개발중)
└── global/
    ├── config/       # Security, JWT, Redis 등 설정
    ├── security/     # JWT 필터
    └── exception/    # 전역 예외 처리
```

---

## 설계 문서

- [인증 설계](https://github.com/Mockly-Company/.github/blob/main/profile/Auth/README.md) — OAuth 2.1 PKCE 플로우, JWT 전략, Refresh Token Rotation
- [결제 설계](https://github.com/Mockly-Company/.github/blob/main/profile/Payment/README.md) — Outbox Pattern, 구독 상태 머신, 멱등성 처리

---

## API 문서

- **Postman**: [Mockly API
  Collection](https://www.postman.com/the-greatest-piggy-team/portone-public-api/collection?sideView=agentMode)
- **Swagger UI**: 로컬 실행 후 `http://localhost:8080/swagger-ui.html` (dev 프로파일)

---

## 기술 블로그

해당 프로젝트와 관련해 작성한 기술 글입니다.

- [Transactional Outbox 패턴 도입기 - 결제 후 스케줄 예약하기](https://blog.kancho.co/posts/Transactional-Outbox-%ED%8C%A8%ED%84%B4-%EB%8F%84%EC%9E%85%EA%B8%B0-%EA%B2%B0%EC%A0%9C-%ED%9B%84-%EC%8A%A4%EC%BC%80%EC%A4%84-%EC%98%88%EC%95%BD%ED%95%98%EA%B8%B0/)
- [요청마다 다른 SecurityFilterChain을 적용해보자](https://blog.kancho.co/posts/%EC%9A%94%EC%B2%AD%EB%A7%88%EB%8B%A4-%EB%8B%A4%EB%A5%B8-SecurityFilterChain%EC%9D%84-%EC%A0%81%EC%9A%A9%ED%95%B4%EB%B3%B4%EC%9E%90/)
- [OAuth 2.1 - OAuth 2.0과 무엇이 다를까](https://blog.kancho.co/posts/OAuth-2.1-OAuth-2.0%EA%B3%BC-%EB%AC%B4%EC%97%87%EC%9D%B4-%EB%8B%A4%EB%A5%B8%EA%B9%8C/)

---

## 로컬 실행 방법

**사전 요구사항**: Docker, JDK 21

**1. 저장소 클론**

```bash
git clone https://github.com/Mockly-Company/mockly-server.git
cd mockly-server
```

**2. `.env` 파일 생성**

프로젝트 루트에 `.env` 파일을 생성하고 아래 환경변수를 설정합니다.

| 변수명 | 설명 |
|---|---|
| `POSTGRES_HOST` | PostgreSQL 호스트 |
| `POSTGRES_USER` | PostgreSQL 사용자명 |
| `POSTGRES_PASSWORD` | PostgreSQL 비밀번호 |
| `POSTGRES_DB` | PostgreSQL 데이터베이스명 |
| `POSTGRES_PORT` | PostgreSQL 포트 |
| `REDIS_HOST` | Redis 호스트 |
| `REDIS_PASSWORD` | Redis 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 (HS512) |
| `GOOGLE_ANDROID_CLIENT_ID` | Google OAuth 클라이언트 ID |
| `OPENAI_API_KEY` | OpenAI API 키 |
| `PORTONE_API_SECRET` | PortOne API 시크릿 |
| `PORTONE_WEBHOOK_SECRET` | PortOne 웹훅 시크릿 |
| `PORTONE_STORE_ID` | PortOne 상점 ID |
| `PORTONE_CHANNEL_KEY` | PortOne 채널 키 |

**3. 실행**

```bash
docker compose up --build
```

**4. Swagger UI 접속**

```
http://localhost:8080/swagger-ui.html
```
