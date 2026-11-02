# Dasigochim Backend

다시고침 청년주택 3D 공간 민원 서비스의 Spring Boot API 서버입니다.

## Docker로 전체 실행

Docker만 설치되어 있으면 백엔드와 PostgreSQL을 함께 실행할 수 있습니다.

```bash
cp .env.example .env
# .env의 POSTGRES_PASSWORD와 JWT_SECRET을 로컬 값으로 변경
docker compose up -d --build
docker compose ps
```

- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

기본 `local` 프로필에서는 Google 로그인을 실제 ID token 검증 방식으로 처리하고, AI·푸시·HWP 생성은 목 처리합니다. 신고 플로우의 AI만 실제 서버로 바꿀 수 있습니다([AI 서버 연동](#ai-서버-연동) 참고). 업로드 파일은 Gradle 직접 실행 시 `build/uploads`, Docker 실행 시 `/app/uploads` named volume에 저장합니다.

Docker 환경의 DB와 업로드 파일은 각각 named volume에 저장되어 컨테이너를 재생성해도 유지됩니다.

```bash
# 로그 확인
docker compose logs -f backend

# 컨테이너 종료(데이터 볼륨 유지)
docker compose down

# 컨테이너와 로컬 DB/업로드 볼륨까지 삭제
docker compose down -v
```

`docker compose down -v`는 로컬 데이터를 삭제하므로 초기화가 필요한 경우에만 사용하세요.

## AI 서버 연동

신고 플로우의 확인 질문·신고서 초안을 목 대신 [ai-server](../ai-server)(FastAPI + Gemini)로 처리합니다.
`AI_MODE=mock`(기본)이면 지금까지처럼 목 응답을 씁니다.

```bash
# 1) AI 서버 먼저 (별도 compose 프로젝트, 8000 포트)
cd ../ai-server && docker compose up -d --build
curl http://localhost:8000/health      # api_key_loaded: true 확인

# 2) 백엔드를 http 모드로
cd ../backend
AI_MODE=http docker compose up -d --build backend
```

`AI_MODE`를 바꾼 뒤에는 반드시 `up -d`로 **컨테이너를 재생성**해야 합니다. `docker compose restart`는 환경변수를 다시 읽지 않습니다.

### 호출 흐름

프런트의 신고 플로우 API 두 개가 ai-server의 4개 엔드포인트로 펼쳐집니다.

| 백엔드 API | ai-server 호출 | 비고 |
|---|---|---|
| `POST /api/v1/ai/report-flow/next-question` (1번째) | `① /v1/vision/analyze` → `② /v1/questions/next` | 약 7초 |
| `POST /api/v1/ai/report-flow/next-question` (2·3번째) | `② /v1/questions/next` | ①은 캐시 재사용, 약 2초 |
| `POST /api/v1/ai/report-flow/draft` | `④ /v1/drafts/generate` → `③ /v1/departments/classify` | 약 5초 |
| `POST /api/v1/reports/{id}/submit` | `⑤ /v1/insights/generate` | 커밋 후 **비동기**, 약 7초 |

확인 질문은 한 번에 하나씩 만듭니다. 클라이언트가 지금까지의 질문·답변을 `answers` 로 함께 보내면
그 내용을 이력으로 넘겨 아직 확인되지 않은 것을 묻습니다. 첫 호출은 `answers` 를 빈 배열로 보내고,
응답의 `last` 가 `true` 면 다음은 초안 생성 차례입니다.

- 사진은 신고에 첨부된 첫 장을 base64로 실어 보냅니다. 사진이 없으면 판독할 수 없어 502로 실패합니다.
- ①의 판독 결과는 `report-{id}` 키로 30분간 메모리에 캐시해 질문·초안 단계가 같은 판독을 공유합니다.
- 위험 등급은 AI의 4단계(low/medium/high/critical)를 백엔드 3단계로 접습니다 (`critical` → `HIGH`).
- 부서는 ai-server가 돌려준 코드를 `departments.code`로 조회합니다. 양쪽 코드 집합(`FACILITY`/`SAFETY_CENTER`/`GENERAL_AFFAIRS`)이 일치해야 합니다.
- `POST /api/v1/ai/analyze-content`, `POST /api/v1/ai/draft-from-text`는 사진 없는 텍스트 전용 계약이라 ai-server에 대응 경로가 없습니다. `http` 모드에서도 목 응답을 반환합니다.

### 위험도 근거와 담당자 인사이트 (관리자용)

④가 함께 주는 위험 등급 판단 근거는 `reports.risk_level_rationale` / `risk_level_changed`에 저장합니다.
⑤ 인사이트는 신고 제출이 **커밋된 뒤 비동기로** 생성해 `report_insights`에 넣습니다(신고 1건에 1행).
제출 응답을 붙잡지 않으므로 접수는 즉시 끝나고, 생성이 실패해도 접수에는 영향이 없습니다.

둘 다 담당자 내부용이라 제보자 대면 응답(`ReportResponse`)에는 넣지 않고 관리자 전용 경로로만 노출합니다.

```bash
# 관리자 토큰 (local 프로필 시드 계정)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/admin/auth/login   -H 'Content-Type: application/json' -d '{"loginId":"admin","password":"admin1234"}'   | python -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])')

# 위험도 근거 + 인사이트 조회 (인사이트는 생성 전이면 null)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/admin/reports/6/insight

# 재생성 — 비동기 생성이 실패했거나 이 기능 이전에 접수된 신고에 쓴다
curl -s -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/admin/reports/6/insight
```

인사이트를 보여줄 관리자 화면은 아직 없습니다. 현재는 위 API로만 확인합니다.

## IDE에서 백엔드만 실행

Java 21이 설치되어 있다면 PostgreSQL만 Docker로 실행하고 애플리케이션은 IDE 또는 Gradle로 실행할 수 있습니다.

```bash
docker compose up -d postgres
./gradlew bootRun
```

## Google 로그인

프론트의 Google Identity Services 버튼이 발급한 ID token을 `/api/v1/auth/google`로 전달합니다. 백엔드는 Google 공개키를 이용해 서명, 발급자, 만료 시간과 `GOOGLE_CLIENT_ID` audience를 검증한 뒤 서비스 JWT를 발급합니다.

프론트 `VITE_GOOGLE_CLIENT_ID`와 백엔드 `GOOGLE_CLIENT_ID`는 동일한 Web Client ID여야 합니다. Google Cloud Console의 승인된 JavaScript 원본에는 로컬 개발 주소 `http://localhost:5175`를 등록합니다.

로컬 관리자 계정은 애플리케이션 시작 시 생성됩니다.

- ID: `admin`
- Password: `admin1234`

운영 프로필에서는 이 초기화가 동작하지 않습니다.

## 검증

```bash
./gradlew test
./gradlew build
```

환경변수 목록은 `src/main/resources/application.yml`을 참고하세요. 운영 환경에서는 `JWT_SECRET`, DB 접속 정보 및 외부 연동 구현을 반드시 교체해야 합니다.

## 주요 환경변수

| 변수 | 설명 | 로컬 기본값 |
|---|---|---|
| `BACKEND_PORT` | 호스트에 공개할 API 포트 | `8080` |
| `POSTGRES_PORT` | 호스트에 공개할 PostgreSQL 포트 | `5432` |
| `POSTGRES_DB` | DB 이름 | `dasigochim` |
| `POSTGRES_USER` | DB 사용자 | `dasigochim` |
| `POSTGRES_PASSWORD` | DB 비밀번호 | `dasigochim` |
| `JWT_SECRET` | JWT HMAC Base64 키 (`openssl rand -base64 64`) | 필수 |
| `CORS_ALLOWED_ORIGINS` | 허용할 프론트 Origin 목록 | `http://localhost:5175` |
| `GOOGLE_CLIENT_ID` | Google ID token audience 검증용 Web Client ID | 필수 |

`AI_API_KEY`는 향후 실제 AI 연동을 위한 예시 변수이며 현재 목 구현체는 읽지 않습니다.

`.env`는 Git에서 제외됩니다. 실제 비밀번호나 JWT 키를 `.env.example`, Compose, Git 저장소에 커밋하면 안 됩니다.
