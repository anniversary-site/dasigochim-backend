# 데이터베이스

PostgreSQL 16(`pgvector/pgvector:pg16`)을 쓰고, 스키마는 **Flyway**만 바꿔요. JPA는 `ddl-auto: validate`라서 엔티티와 스키마가 다르면 애플리케이션이 뜨지 않습니다. 마이그레이션 파일은 `src/main/resources/db/migration`에 있어요.

[← README로 돌아가기](../README.md)

> 이 프로젝트는 처음에 대학 캠퍼스 안전신고 서비스로 시작했다가 V17부터 청년주택 3D 공간 민원으로 바뀌었어요. 그래서 V2~V16에 캠퍼스 건물 관련 마이그레이션이 남아 있습니다. 이미 적용된 마이그레이션은 고치지 말고 새 버전을 추가해 주세요.

## Flyway 마이그레이션

| 버전 | 파일 | 내용 |
|---|---|---|
| V1 | `V1__init_schema.sql` | 기본 스키마: `departments`, `users`, `admins`, `buildings`, `reports`, `report_photos`, `report_activity_logs`, `notifications` |
| V2 | `V2__seed_departments_buildings.sql` | 부서 3개(`FACILITY`, `SAFETY_CENTER`, `GENERAL_AFFAIRS`)와 캠퍼스 건물 시드 |
| V3 | `V3__generalize_social_login.sql` | `users.kakao_id` → `provider` + `provider_id`로 소셜 로그인 일반화 |
| V4 | `V4__add_report_location_description.sql` | `reports.location_description` 추가 |
| V5 | `V5__add_report_insight.sql` | 위험도 근거 컬럼(`risk_level_rationale`, `risk_level_changed`)과 `report_insights` 테이블 |
| V6 | `V6__add_admin_notification_recipients.sql` | `notifications.admin_id` 추가, 수신자는 입주민·관리자 중 하나만 |
| V7 | `V7__link_admins_to_social_users.sql` | `admins.user_id`로 Google 계정 연결, 로컬 ID/비밀번호는 선택값으로 |
| V8 | `V8__seed_verified_campus_buildings.sql` | 좌표가 검증된 캠퍼스 건물 추가 |
| V9 | `V9__allow_buildings_without_verified_coordinates.sql` | `buildings.lat/lng` nullable |
| V10 | `V10__seed_campus_building_catalog.sql` | 캠퍼스 건물·시설 목록 시드 |
| V11 | `V11__add_department_duties.sql` | 부서 `contact`·`active`, `department_duties`(C-1 코퍼스), `department_duty_corpus` |
| V12 | `V12__seed_past_reports.sql` | C-5 과거사례용 시스템 유저와 과거 신고 8건 (V17에서 삭제됨) |
| V13 | `V13__enable_pgvector.sql` | `vector` 확장과 `report_embeddings(vector(768))` — 아직 미사용 |
| V14 | `V14__add_user_phone.sql` | `users.phone` 추가 |
| V15 | `V15__correct_jeong_hasang_hall_coordinates.sql` | 캠퍼스 건물 한 곳 좌표 보정 |
| V16 | `V16__backfill_verified_campus_building_coordinates.sql` | 캠퍼스 건물 좌표 일괄 보정 |
| V17 | `V17__housing_space_complaints.sql` | **청년주택 3D 공간 민원으로 전환**: 캠퍼스 시드 정리, 단지(`sido`/`sigungu`), `building_units`(호수 → 세대 타입), `building_spaces`(공용공간), 신고의 공간·3D 핀 컬럼, 입주민 프로필, 주택 민원용 부서 이름·업무범위, 주택 과거사례 |
| V18 | `V18__nationwide_regions_and_housing.sql` | 전국 시·도/시·군·구 `regions` 테이블과 전국 청년주택 목록, `buildings.supply_status` |
| V19 | `V19__admin_scopes_and_departments.sql` | `admins.role`(기존 관리자는 `SUPER`), `admin_scopes`, 부서 4개 추가(`ELEVATOR`, `ELECTRIC`, `FIRE_GAS`, `SANITATION`)와 업무범위 재배치, `reports.resolved_at` |
| V20 | `V20__mapo_housing_3d_scenes.sql` | 마포구 청년주택 4곳의 3D 장면 연결 (세대 표 `building_units`, 공용공간 `building_spaces`). 세대 표는 공개 평면도를 읽은 임시 표예요 |

## 더미 민원 데이터 (`scripts/dummy-reports`)

로컬·시연용 더미 민원이에요. **Flyway 마이그레이션이 아니라서 운영 DB에 자동으로 들어가지 않아요.** V19까지 적용된 DB에 넣어야 합니다(`resolved_at` 컬럼과 새 부서가 필요해요).

| 파일 | 설명 |
|---|---|
| `generate_dummy_reports.py` | 더미 민원 SQL 생성기. 시드를 고정해서 몇 번을 돌려도 같은 SQL이 나와요 |
| `buildings.json` | 단지 목록 (이름, 시·군·구, 공급 단계, 3D 여부). 단지는 id 대신 이름으로 찾아요 |
| `eseo_units.json` | 공덕역 e·seo 세대 중 세대 3D 평면이 있는 세대 |
| `eseo_50_values.sql` | 팀에서 만든 공덕역 e·seo 민원 50건 (생성기가 그대로 사용, 단독 실행문은 아님) |
| `dummy_reports.sql` | 생성된 결과. 다시 실행하면 기존 더미를 지우고 새로 넣어요 |
| `clear_dummy_reports.sql` | 더미 민원 전부 삭제 (`tracking_id LIKE 'DM-%'`) |

생성 규칙 요약 (자세한 건 `scripts/dummy-reports/README.md`):

- 입주 단지는 민원 많음(18~35건) 15% · 보통(6~14건) 35% · 적음(1~4건) 35% · 없음 15%, 준공 단지는 0~3건, 착공·인가 단지는 0건
- 최근 180일 사이에 분포하고, 오래된 민원일수록 처리 완료 비율이 높아요
- 추적번호는 `DM-`으로 시작해서 실제 신고(`DG-`)와 구분돼요

```bash
# 넣기
docker exec -i dasigochim-postgres-1 psql -U dasigochim -d dasigochim < scripts/dummy-reports/dummy_reports.sql

# 지우기
docker exec -i dasigochim-postgres-1 psql -U dasigochim -d dasigochim < scripts/dummy-reports/clear_dummy_reports.sql

# 유형·비율을 바꿨다면 다시 생성
python3 scripts/dummy-reports/generate_dummy_reports.py > scripts/dummy-reports/dummy_reports.sql
```
