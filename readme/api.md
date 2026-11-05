# API 목록

`@RestController` 클래스에서 그대로 뽑은 목록이에요. 실행 중인 서버에서는 Swagger UI(`/swagger-ui.html`)로 요청·응답 스키마까지 볼 수 있어요.

[← README로 돌아가기](../README.md)

## 공통 사항

- `/api/v1/**` 응답은 `ApiResponse`로 감싸져요 (`common/response/ApiResponse`). `/internal/v1/**`은 감싸지 않습니다.
- 인증 헤더: `Authorization: Bearer <accessToken>`
- 인증 표기
  - **공개**: 토큰 없이 호출 가능
  - **USER**: 입주민 JWT (`ROLE_USER`)
  - **ADMIN**: 관리자 JWT (`ROLE_ADMIN`). 추가로 `AdminAccessService`가 담당 구역 밖이면 403
  - **AGENT**: `X-Agent-Token` 헤더 (AI 서버 전용)
- 인증이 없거나 잘못되면 401 `INVALID_TOKEN`(또는 `INVALID_AGENT_TOKEN`)을 돌려줘요.
- 규칙 출처: `common/config/SecurityConfig`

## 인증 — `AuthController`, `GuestAuthController`

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| POST | `/api/v1/auth/google` | Google ID token(`idToken`)으로 로그인, 토큰 쌍 발급 | 공개 |
| POST | `/api/v1/auth/refresh` | 리프레시 토큰으로 토큰 쌍 재발급 | 공개 |
| POST | `/api/v1/admin/auth/login` | 관리자 ID(`loginId`)/비밀번호 로그인 | 공개 |
| POST | `/api/v1/auth/guest` | 심사용 게스트 로그인(`role`, `code`, 관리자면 `adminRole`). `GUEST_LOGIN_ENABLED=true`일 때만 등록됨 | 공개 |

## 청년주택 — `BuildingController`

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/api/v1/buildings` | 단지 목록 (`query`, `sido`, `sigungu` 필터) | 공개 |
| GET | `/api/v1/buildings/regions` | 시·도 / 시·군·구 목록 | 공개 |
| GET | `/api/v1/buildings/{id}` | 단지 상세 | 공개 |
| GET | `/api/v1/buildings/{id}/spaces` | 단지의 공용공간 목록 | 공개 |
| GET | `/api/v1/buildings/{id}/units` | 단지의 세대 목록 (신고 화면 세대 선택용) | 공개 |
| GET | `/api/v1/buildings/{id}/units/lookup` | 동(`dong`, 선택)·호(`ho`)로 세대 타입·3D 장면 조회 | 공개 |

## 신고 — `ReportController`

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| POST | `/api/v1/reports/draft` | 임시 신고(`RECEIVING`) 생성 | USER |
| POST | `/api/v1/reports/{id}/photos` | 사진 업로드 (multipart `photos`, 신고당 최대 5장) | USER |
| PATCH | `/api/v1/reports/{id}/location` | 위치 지정 (단지, 공간, 3D 핀 등) | USER |
| PATCH | `/api/v1/reports/{id}` | 임시 신고 수정 (요약, 내용, 실명/익명, 원 신고) | USER |
| GET | `/api/v1/reports/{id}/duplicates` | 같은 단지 최근 30일 신고 중 중복 후보(최대 5건) | USER |
| POST | `/api/v1/reports/{id}/report-file` | HWP 신고서 생성 | USER |
| POST | `/api/v1/reports/{id}/submit` | 제출 (`RECEIVED`로 전환, 추적번호 발급) | USER |
| DELETE | `/api/v1/reports/{id}` | 임시 신고 삭제 | USER |
| GET | `/api/v1/reports/feed` | 메인 목록. `filter=mine`(내 신고) 또는 `others`(같은 단지 다른 입주민) | USER |
| GET | `/api/v1/reports/{trackingId}` | 추적번호로 조회. 본인 또는 같은 단지 입주민만 볼 수 있어요 | 공개 경로 (서비스에서 검사) |

## AI 신고 플로우 — `AiController`

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| POST | `/api/v1/ai/report-flow/next-question` | 확인 질문 하나 받기 (`reportId`, `incidentDescription`, `answers`) | USER |
| POST | `/api/v1/ai/report-flow/draft` | 신고서 초안·위험도·부서 생성 (`reportId`, `locationDescription`, `incidentDescription`, `answers`) | USER |
| POST | `/api/v1/ai/analyze-location` | 좌표(`lat`, `lng`)로 위치 후보 추천 | USER |
| POST | `/api/v1/ai/analyze-content` | 텍스트 기반 내용 분석 (항상 목 응답) | USER |
| POST | `/api/v1/ai/draft-from-text` | 텍스트로 초안 생성 (항상 목 응답) | USER |

## 내 정보 — `MeController`

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/api/v1/me/profile` | 내 프로필 | USER |
| PATCH | `/api/v1/me/profile` | 프로필 수정 (`name`, `phone`, `buildingId`, `dong`, `ho`) | USER |
| GET | `/api/v1/me/reports` | 내 신고 목록 | USER |
| GET | `/api/v1/me/notifications` | 내 알림 목록 | USER |
| PATCH | `/api/v1/me/notifications/{id}/read` | 알림 읽음 처리 | USER |

## 관리자 — `AdminReportController`

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/api/v1/admin/me` | 내 관리자 정보 (역할, 담당 구역) | ADMIN |
| GET | `/api/v1/admin/stats/summary` | 담당 구역의 상태별 건수·오늘 신규 건수 | ADMIN |
| GET | `/api/v1/admin/departments` | 부서별 미처리 건수 | ADMIN |
| GET | `/api/v1/admin/buildings/{id}/space-map` | 단지의 3D 장면 목록과 접수된 신고(핀 좌표 포함) | ADMIN (단지 범위 검사) |
| GET | `/api/v1/admin/reports` | 신고 목록 (`status`, `departmentId`, `q`, `page`, `size`(기본 20), `sort`(기본 `latest`)) | ADMIN |
| GET | `/api/v1/admin/reports/{id}` | 신고 상세 | ADMIN |
| GET | `/api/v1/admin/reports/{id}/report-file` | HWP 신고서 URL 조회 | ADMIN |
| POST | `/api/v1/admin/reports/{id}/report-file` | HWP 신고서 생성 | ADMIN |
| GET | `/api/v1/admin/reports/{id}/insight` | 위험도 근거 + 인사이트 (생성 전이면 인사이트는 `null`) | ADMIN |
| POST | `/api/v1/admin/reports/{id}/insight` | 인사이트 재생성 | ADMIN |
| PATCH | `/api/v1/admin/reports/{id}/status` | 상태 변경 (`REVIEWING`만 허용) | ADMIN |
| PATCH | `/api/v1/admin/reports/{id}/department` | 담당 부서 재배정 (`departmentId`) | ADMIN |
| PATCH | `/api/v1/admin/reports/{id}/risk-level` | 위험도 조정 (`riskLevel`) | ADMIN |
| POST | `/api/v1/admin/reports/{id}/resolve` | 처리 완료 (`actionNote` 필수) | ADMIN |

## 관리자 알림 — `AdminNotificationController`

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/api/v1/admin/notifications` | 내 알림 목록 | ADMIN |
| PATCH | `/api/v1/admin/notifications/{id}/read` | 알림 읽음 처리 | ADMIN |

## 내부 API (AI 서버 전용) — `InternalAiController`

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| POST | `/internal/v1/rag/duty-search` | C-1 부서 업무범위 검색 | AGENT |
| POST | `/internal/v1/departments/resolve` | C-2 부서 코드 검증·기본 부서 강등 | AGENT |
| POST | `/internal/v1/cases/search` | C-5 과거사례 검색 + 집계 | AGENT |

자세한 동작은 [architecture.md의 AI 연동](architecture.md#ai-연동)을 보세요.

## 기타

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| GET | `/actuator/health` | 헬스체크 | 공개 |
| GET | `/swagger-ui.html`, `/v3/api-docs/**` | API 문서 | 공개 |
| GET | `/files/**` | 업로드 사진·생성된 HWP 파일 | 공개 |
