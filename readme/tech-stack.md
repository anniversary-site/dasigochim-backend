# 기술 스택

버전은 `build.gradle`, `gradle/wrapper/gradle-wrapper.properties`, `Dockerfile`, `compose.yaml`, `.github/workflows/publish-image.yml`에서 그대로 옮겼어요. 버전이 적혀 있지 않은 의존성은 Spring Boot BOM(`io.spring.dependency-management`)이 정한 버전을 씁니다.

[← README로 돌아가기](../README.md)

## 언어와 빌드

| 항목 | 버전 | 비고 |
|---|---|---|
| Java | 21 | Gradle toolchain (`java.toolchain.languageVersion = 21`) |
| Gradle | 8.10.2 | Gradle Wrapper |
| Spring Boot | 3.3.13 | `org.springframework.boot` 플러그인 |
| Spring Dependency Management | 1.1.7 | |

## 애플리케이션 라이브러리

| 라이브러리 | 버전 | 어디에 쓰나요 |
|---|---|---|
| `spring-boot-starter-web` | BOM | REST 컨트롤러, `/files/**` 정적 파일 제공(`common/config/WebConfig`) |
| `spring-boot-starter-security` | BOM | 필터 체인 2개(`common/config/SecurityConfig`): `/internal/**`은 `X-Agent-Token`, 나머지는 JWT. 비밀번호는 BCrypt |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | 0.12.6 | 액세스(30분)·리프레시(14일) 토큰 발급·검증(`common/security/JwtTokenProvider`, `JwtAuthenticationFilter`) |
| `google-api-client` | 2.9.0 | Google ID token 서명·발급자·audience 검증(`auth/service/GoogleSocialOAuthClient`) |
| `spring-boot-starter-data-jpa` (Hibernate) | BOM | 엔티티·리포지토리. `ddl-auto: validate`라서 스키마는 Flyway만 바꿉니다. 관리자 목록 필터는 JPA `Specification`(`admin_report/service/AdminReportService`) |
| `querydsl-jpa` / `querydsl-apt` (jakarta) | 5.1.0 | 의존성과 애너테이션 프로세서로 등록돼 있어요 |
| `flyway-core` / `flyway-database-postgresql` | BOM | `src/main/resources/db/migration`의 V1~V20 마이그레이션 |
| `postgresql` (JDBC 드라이버) | BOM | 런타임 DB 드라이버 |
| `spring-boot-starter-validation` | BOM | 요청 DTO의 `@NotNull`, `@NotBlank`, `@Pattern` 등 |
| `spring-boot-starter-actuator` | BOM | `/actuator/health`(Compose 헬스체크), `health,info`만 노출 |
| `springdoc-openapi-starter-webmvc-ui` | 2.6.0 | Swagger UI `/swagger-ui.html` |
| `mapstruct` / `mapstruct-processor` | 1.6.3 | 의존성과 애너테이션 프로세서로 등록돼 있어요 |
| `hwplib` (kr.dogfoot) | 1.1.10 | HWP 신고서 생성. `templates/dasigochim-report-template.hwp`의 토큰을 신고 내용으로 치환하고 사진을 넣어요(`file/hwp/TemplateHwpReportGenerator`) |
| `metadata-extractor` (drewnoakes) | 2.19.0 | HWP에 넣을 휴대폰 사진의 EXIF 방향을 실제 픽셀에 반영(`TemplateHwpReportGenerator`) |
| Lombok | BOM | `@Getter`, `@RequiredArgsConstructor` 등 |
| `spring-boot-devtools` | BOM | `developmentOnly` |

### 외부 연동 구현 선택

| 연동 | 설정 키 | 구현 |
|---|---|---|
| AI | `app.external.ai` (`AI_MODE`) | `mock` → `MockAiAnalysisClient`, `http` → `HttpAiAnalysisClient` + `AiServerClient` + `AiVisionCache` |
| 소셜 로그인 | `app.external.social-login` | `local` 프로필 기본값 `google` → `GoogleSocialOAuthClient`, `mock` → `MockSocialOAuthClient` |
| HWP 생성 | 프로필 | 기본은 `TemplateHwpReportGenerator`(실제 생성), `mock-hwp` 프로필이면 `MockHwpReportGenerator` |
| 푸시 | — | `MockPushNotificationClient`만 있어요 |
| 파일 저장 | — | `LocalFileStorageService` (`app.file.upload-dir`, 신고당 사진 최대 5장, 파일당 10MB) |

## 데이터베이스

| 항목 | 값 |
|---|---|
| 이미지 | `pgvector/pgvector:pg16` (PostgreSQL 16 + pgvector) |
| pgvector | `V13`에서 `vector` 확장을 켜고 `report_embeddings(vector(768))`를 만들어 두었지만 **아직 쓰지 않아요**. 과거사례 검색은 "같은 단지 먼저 → 최근순" SQL입니다 |

`compose.yaml` 주석에 적힌 대로, `postgres:16-alpine`에서 이 이미지로 바꿨다면 정렬 구현이 달라지므로 `REINDEX DATABASE`를 한 번 실행하세요.

## 테스트

| 라이브러리 | 쓰임새 |
|---|---|
| `spring-boot-starter-test` (JUnit 5) | 단위·통합 테스트 (`useJUnitPlatform()`) |
| `spring-security-test` | 인증이 필요한 API 테스트 |
| H2 (`testRuntimeOnly`) | 테스트 DB. `src/test/resources/application.yml`에서 `MODE=PostgreSQL`, Flyway 끄고 `ddl-auto: create-drop` |
| Testcontainers (`junit-jupiter`, `postgresql`) | 의존성만 등록돼 있고 현재 테스트 코드에서는 쓰지 않아요 |

테스트 파일(`src/test/java/com/dasigochim`): `AuthServiceTest`, `GuestAuthServiceTest`, `AdminScopeIntegrationTest`, `ReportInsightIntegrationTest`, `AiReportFlowIntegrationTest`, `MockAiAnalysisClientTest`, `ReportFlowIntegrationTest`, `ReportSpaceIntegrationTest`, `ReportTest`.

## Docker

`Dockerfile`은 멀티 스테이지 빌드예요.

| 단계 | 베이스 이미지 | 하는 일 |
|---|---|---|
| builder | `eclipse-temurin:21-jdk-alpine` | 의존성을 먼저 받아 캐시한 뒤 `./gradlew bootJar` |
| runtime | `eclipse-temurin:21-jre-alpine` | 비루트 사용자 `dasigochim`으로 실행, `/app/uploads` 생성, `-XX:MaxRAMPercentage=75.0` |

`compose.yaml`은 `backend`와 `postgres` 두 서비스와 named volume 2개(`dasigochim-postgres`, `dasigochim-uploads`)로 구성돼요. 백엔드는 Postgres 헬스체크가 통과한 뒤 뜨고, `host.docker.internal`로 호스트의 AI 서버(8000)에 접근합니다.

## CI: 이미지 배포 (GHCR)

`.github/workflows/publish-image.yml`

- 트리거: `main` 브랜치 push, `v*` 태그 push, 수동 실행(`workflow_dispatch`)
- 사용 액션: `actions/checkout@v4`, `docker/login-action@v3`, `docker/metadata-action@v5`, `docker/build-push-action@v6`
- 이미지: `ghcr.io/<저장소 소유자>/backend`
- 태그: 브랜치 이름, 태그 이름, 커밋 SHA(접두사 없음)
- 인증: 워크플로의 `GITHUB_TOKEN` (`packages: write` 권한)

배포 서버는 [dasigochim-deployment](https://github.com/anniversary-site/dasigochim-deployment)에서 이 이미지를 받아 띄웁니다.
