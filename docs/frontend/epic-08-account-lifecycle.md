# 에픽 #8 회원 계정 라이프사이클 백엔드 API 변경 안내

## 1. 변경 개요

- **변경 내용**
  - 동일 디바이스에서 다중 계정 로그인 시 충돌하던 `session` 테이블 제약(`uk_session_device_id`)을 `(user_id, device_id)` 복합 유니크(`uk_session_user_device`)로 변경
  - Apple OAuth 2.1 + PKCE 로그인(`POST /api/auth/login/apple/code`) 추가 및 최초 가입 시 `Free` 플랜 자동 배정
  - `GET /api/auth/me` (및 로그인 응답 `user`)에 `hasAgreedToTerms`, `jobCategory`, `jobRole`, `experienceYears` 필드 확장
  - 약관 동의 저장(`POST /api/users/agreements`), 내 프로필 기본 정보 수정(`PATCH /api/users/me`), 회원 탈퇴(`DELETE /api/users/{userId}`) 엔드포인트 신설
- **변경 이유**: iOS 심사를 위한 Apple 로그인 및 계정 삭제(회원 탈퇴) 의무 요건 충족, 신규 가입자 약관 동의 게이트 활성화, 마이페이지 프로필 관리 지원, 다중 계정 기기 세션 충돌 버그 해소
- **포함 범위**: `V3__account_lifecycle_and_device_session.sql`, `AuthController`, `UserController` (`/api/users/**`), `AppleOAuthService`
- **제외 항목**: 휴대폰 본인인증(OTP) 엔드포인트 (본 에픽 범위 외)

---

## 2. API 변경 사항

### 2.1 `GET /api/auth/me` 응답 필드 확장
- **이전 필드**: `id`, `email`, `name`
- **이후 필드**:
  ```json
  {
    "success": true,
    "data": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "user@example.com",
      "name": "홍길동",
      "hasAgreedToTerms": true,
      "jobCategory": "DEVELOPER",
      "jobRole": "BACKEND",
      "experienceYears": 2
    },
    "error": null,
    "message": null,
    "timestamp": 1760000000000
  }
  ```
- **참고**: 약관 미동의 신규 가입자는 `"hasAgreedToTerms": false`로 반환되며, 프로필 미입력 필드는 `null`입니다. `POST /api/auth/login/{provider}/code` 응답의 `data.user` 객체도 동일 구조를 따릅니다.

### 2.2 `POST /api/auth/login/apple/code` (신규)
- **Request Body**:
  ```json
  {
    "code": "c12345...",
    "codeVerifier": "verifier...",
    "redirectUri": "mockly://oauth/callback",
    "deviceInfo": {
      "deviceId": "ios-device-uuid",
      "deviceName": "iPhone 16 Pro"
    },
    "locationInfo": {
      "latitude": 37.4979,
      "longitude": 127.0276
    },
    "fullName": "홍길동"
  }
  ```
- **Response**: `POST /api/auth/login/google/code`와 동일한 `{ accessToken, refreshToken, expiresIn, user }` 구조
- **오류 코드**: 인증 코드 또는 Apple ID Token 검증 실패 시 `401 INVALID_APPLE_TOKEN`

### 2.3 `POST /api/users/agreements` (신규)
- **Request Body**:
  ```json
  {
    "agreements": [
      { "type": "age14", "agreed": true },
      { "type": "terms", "agreed": true },
      { "type": "privacy", "agreed": true },
      { "type": "location", "agreed": false },
      { "type": "marketing", "agreed": false }
    ]
  }
  ```
- **검증 규칙**: 필수 항목(`age14`, `terms`, `privacy`) 중 하나라도 누락되거나 `agreed: false`이면 `400 VALIDATION_ERROR`
- **Response**: `200 OK` (`data: null`)

### 2.4 `PATCH /api/users/me` (신규)
- **Request Body**:
  ```json
  {
    "name": "새닉네임",
    "jobCategory": "DEVELOPER",
    "jobRole": "FRONTEND",
    "experienceYears": 2
  }
  ```
  - `name`: 필수, 공백 제외 1~50자
  - `experienceYears`: 선택, 0~40 정수
- **Response**: 갱신된 `UserInfo` (`id`, `email`, `name`, `hasAgreedToTerms`, `jobCategory`, `jobRole`, `experienceYears`)

### 2.5 `DELETE /api/users/{userId}` (신규)
- **권한**: JWT 인증 사용자 본인의 `userId`만 허용 (타인 `userId` 요청 시 `403 FORBIDDEN`)
- **서버 처리**: 활성 유료 구독 결제 스케줄 취소 및 `CANCELED` 종료, 등록 결제수단 비활성화, 전체 기기 세션·리프레시 토큰 삭제, 현재 액세스 토큰 블랙리스트 등록, 계정 이메일·이름·소셜 식별자 비식별화(동일 소셜 계정 재가입 허용)
- **Response**: `200 OK` (`data: null`)

---

## 3. 프론트 수정 대상

### 이번 배포 필수
- `@mockly/api` `getUserProfile.ts`: 서버가 반환하는 `hasAgreedToTerms` 불리언 값 연동 점검 (신규 가입자 `/agreement` 화면 진입 후 `saveUserAgreements` 저장 동작 확인)
- `@mockly/api` `loginWithPKCECode.ts` & `AuthService.apple.ts`: Apple 최초 로그인 시 전달되는 `fullName`(선택) 필드 포함 전송 연동
- `@mockly/api` `patchUserProfile.ts` 신설 및 마이페이지 기본 프로필 편집·회원 탈퇴(`deleteUser.ts`) 모달 연동

### 후속 API 대기
- 없음 (에픽 #8 계정 라이프사이클 API 전수 구현 완료)

### 선택 개선 항목
- 면접 생성 기본값에 `GET /api/auth/me`의 `jobCategory`, `jobRole`, `experienceYears` 자동 프리필 적용

---

## 4. 호환성 및 확인 기준

- **Breaking Change 여부**: 없음. 기존 `GET /api/auth/me` 필드는 그대로 유지되며 신규 필드가 추가되었습니다. 단, 서버 배포 시 약관 미동의 기존/신규 계정에 `hasAgreedToTerms: false`가 내려가므로 앱의 `/agreement` 게이트가 실제로 활성화됩니다.
- **유지 계약**: `POST /api/users/agreements`와 `DELETE /api/users/{userId}`는 이미 `@mockly/api`에 정의된 경로·페이로드와 100% 동일합니다.
- **최종 계약 기준**: `./gradlew openapi3`로 생성된 `openapi3.yaml` (`AuthMeDocs`, `LoginWithAppleCodeDocs`, `UserDocs`) 기준.
