# Epic #10 — 동네면접(Local Interview Meetups) MVP API 안내

## 1. 변경 개요

- **목적**: 모바일 앱(`@mockly/api/src/local-interview-meetups`)에 Mock 어댑터 기준으로 선구현되어 있던 동네면접 오프라인 모임 API 8종을 백엔드 실서버에 구현했습니다.
- **변경 범위**:
  - `GET /api/local-interview-meetups` — 반경(`latitude`, `longitude`, `radiusMeters`) XOR 지도 영역(`swLat`, `swLng`, `neLat`, `neLng`) 기반 검색 및 필터·정렬·1-based 페이지네이션
  - `GET /api/local-interview-meetups/{meetupId}` — 모임 상세 조회 (방장·`APPROVED` 참여자에게만 `fullAddress` 노출, 미승인 사용자에게는 `null` 반환)
  - `POST /api/local-interview-meetups` — 장소 확정(`PLACE_CONFIRMED`) 오프라인(`OFFLINE`) 모임 생성 및 생성자 자동 방장 등록 (`currentParticipants = 1`)
  - `GET /api/local-interview-meetups/me` — 내가 개설했거나 참여 신청한 모임 목록 (`role`, `myParticipationStatus`, `pendingApplicantCount`, `endsAt`)
  - `POST /api/local-interview-meetups/{meetupId}/participants` — 모임 참여 신청 (`PENDING`, 이전 `CANCELLED` 이력 재신청 허용)
  - `DELETE /api/local-interview-meetups/{meetupId}/participants/me` — 내 참여 신청 취소 / 모임 나가기 (`APPROVED` 취소 시 `currentParticipants` 감소, 방장 탈퇴 차단)
  - `GET /api/local-interview-meetups/{meetupId}/participants` — 방장 전용 신청자·참여자 전체 목록 조회
  - `PATCH /api/local-interview-meetups/{meetupId}/participants/{userId}` — 방장 전용 승인(`APPROVED`) / 거절(`REJECTED`) 처리 (`PESSIMISTIC_WRITE` 잠금 기반 정원 보호)
- **제외 항목**:
  - 장소 미확정(`AREA_ONLY` 등) 모임 생성, 모임 내 실시간 채팅 및 후기/노쇼 신고 기능 (후속 단계)

---

## 2. API 변경 사항

### 2.1 `GET /api/local-interview-meetups` (신규)

- **쿼리 파라미터**:
  - 반경 모드: `latitude`, `longitude`, `radiusMeters` (선택, 기본값 `3000`, 최대 `50000`)
  - 영역 모드: `swLat`, `swLng`, `neLat`, `neLng`
  - **배타 조건(XOR)**: 반경 파라미터와 영역 파라미터를 동시에 전달하거나 모두 누락하면 `400 VALIDATION_ERROR`
  - 필터(선택): `jobCategory` (`DEVELOPER` | `DESIGNER` | `PRODUCT_MANAGER`), `jobRole` (`BACKEND` | `FRONTEND` | `AI_ML` | `DATA_ENGINEERING`), `interviewType` (`TECHNICAL` | `CULTURE_FIT` | `PORTFOLIO`), `experienceLevel` (`ENTRY` | `JUNIOR` | `MID` | `SENIOR`)
  - 정렬/페이징: `sort` (`DISTANCE` | `STARTS_AT`), `page` (1-based, 기본 `1`), `size` (기본 `20`, 최대 `50`) — Bounding Box + Haversine DB 쿼리 및 `COUNT` 쿼리로 서버 메모리 전량 로딩 없이 SQL 레벨에서 페이징 처리됩니다.
- **성공 응답 (`200 OK`)**: `meetups[]` (`distanceMeters`는 반경 검색 시 정수 m, 영역 검색 시 `null`) + `pagination` (`page`, `size`, `totalElements`, `totalPages`, `hasNext`)

### 2.2 `GET /api/local-interview-meetups/{meetupId}` (신규)

- **핵심 필드**:
  - `fullAddress`: 방장(`amIHost == true`) 또는 승인된 참여자(`myParticipationStatus == "APPROVED"`)에게만 반환되며, 미참여자·`PENDING`·`REJECTED`·`CANCELLED` 상태에서는 `null`이 반환됩니다.
  - `participantSummaries`: `APPROVED` 상태 참여자 요약 목록 (`nickname`, `isHost`, `jobCategory`, `jobRole`, `experienceYears`, `experienceLevel`) — 모임 모집 조건 복사값이 아닌 각 참여자의 개별 프로필 스냅샷이 노출됩니다.
  - `isFull`: `currentParticipants >= maxParticipants`
  - `recruitDeadline`, `myParticipationStatus`, `amIHost` 포함

### 2.3 `POST /api/local-interview-meetups` (신규)

- **검증 규칙**:
  - `startsAt < endsAt`, `recruitDeadline <= startsAt` 위반 시 `400 VALIDATION_ERROR`
  - `maxParticipants` `2`~`8`, `locationStatus: "PLACE_CONFIRMED"`, `meetingType: "OFFLINE"`
  - 선택 필드 `hostJobCategory`, `hostJobRole`, `hostExperienceYears` (`0`~`50`), `hostExperienceLevel` 전달 시 개설자의 실제 직무·연차 프로필을 방장 참여자 스냅샷으로 기록합니다 (미전달 시 하위 호환 기본값으로 저장).
- **성공 응답 (`201 Created`)**: `{ "meetupId": "<uuid>" }`

### 2.4 `GET /api/local-interview-meetups/me` (신규)

- 개설한 모임(`role: "HOST"`, `myParticipationStatus: "APPROVED"`, `pendingApplicantCount` 집계)과 참여 신청한 모임(`role: "PARTICIPANT"`, `pendingApplicantCount: 0`)을 `endsAt`과 함께 반환합니다.

### 2.5 참여 신청·취소 및 방장 심사 API 4종 (신규)

- `POST /api/local-interview-meetups/{meetupId}/participants` (`{ "message": "...", "jobCategory"?, "jobRole"?, "experienceYears"?, "experienceLevel"? }`) -> `data: null` (`200 OK`)
  - 신청자의 직군·직무·연차(`0`~`50`)·경력 수준 선택 필드를 전달하면 모임 모집 타깃과 무관하게 신청자 본인의 프로필 스냅샷이 기록됩니다 (`experienceYears`만 전달 시 `experienceLevel` 자동 산출, 미전달 시 `0`년/`ENTRY` 기본값).
  - 중복 활성 신청 시 `400 DUPLICATE_RESOURCE`, 본인 모임·마감 경과·정원 마감·거절 이력 시 `400 VALIDATION_ERROR`
- `DELETE /api/local-interview-meetups/{meetupId}/participants/me` -> `data: null` (`200 OK`)
  - 방장 탈퇴 시도 시 `400 VALIDATION_ERROR`
- `GET /api/local-interview-meetups/{meetupId}/participants` -> `{ "participants": [...] }` (`200 OK`, 비방장 `403 FORBIDDEN`)
- `PATCH /api/local-interview-meetups/{meetupId}/participants/{userId}` (`{ "status": "APPROVED" | "REJECTED" }`) -> `data: null` (`200 OK`, 비방장 `403 FORBIDDEN`, 정원 초과 승인 시 `400 VALIDATION_ERROR`)

---

## 3. 프론트 수정 대상

- **이번 배포 필수**:
  - `@mockly/api`의 동네면접 Mock 전용 라우트 고정 우회를 해제하여 `EXPO_PUBLIC_USE_API_MOCK !== 'true'`인 기본 실서버 모드에서 `/api/local-interview-meetups/**` 요청이 백엔드로 전달되도록 전환합니다.
- **후속 API 대기**: 없음 (MVP 8종 전체 구현 완료)
- **선택 개선 항목**:
  - 모임 생성 화면에서 선택 입력인 상세 주소(`fullAddress`) 및 방장 본인 프로필(`hostJobCategory`, `hostJobRole`, `hostExperienceYears`, `hostExperienceLevel`), 참여 신청 시 본인 프로필(`jobCategory`, `jobRole`, `experienceYears`, `experienceLevel`)을 전달하면 참여자 요약 카드에 실제 개인 프로필이 표시됩니다 (Epic #8 사용자 프로필 통합 후 `User` 기본값과 자동 연동 가능).

---

## 4. 호환성 및 확인 기준

- **Breaking Change**: 없음 (`@mockly/api/src/local-interview-meetups/types.ts`의 모든 DTO 필드명·enum 문자열·1-based 페이지 번호와 1:1 일치하며 추가된 프로필 스냅샷 요청 필드는 모두 optional)
- **유지 계약**:
  - 모든 엔드포인트는 인증된 회원 Bearer 토큰(`Authorization: Bearer <accessToken>`)을 기준으로 동작하며 공통 `ApiResponse<T>` 봉투를 반환합니다.
- **릴리스 노트**: 병렬 에픽 마이그레이션(`V2`~`V5`)의 순차 반영을 위해 `spring.flyway.out-of-order: ${FLYWAY_OUT_OF_ORDER:true}`로 설정되어 있으며, 모든 병렬 마이그레이션이 `main`에 통합된 이후 운영 환경에서 `FLYWAY_OUT_OF_ORDER=false`로 잠글 수 있습니다.
- **계약 기준**: Spring REST Docs 및 `./gradlew openapi3`로 생성된 `openapi3.yaml`을 최종 필드 계약 기준으로 삼습니다.
