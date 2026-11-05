# 민원 더미데이터 (로컬·시연용)

청년주택마다 민원이 많은 곳 · 보통인 곳 · 적은 곳 · 없는 곳이 섞이도록 만든 더미 민원.
Flyway 마이그레이션이 아니라서 운영 DB 에는 자동으로 들어가지 않는다.

- 입주 단지: 많음(18~35건) 15% · 보통(6~14건) 35% · 적음(1~4건) 35% · 없음 15%
- 준공 단지: 0~3건, 착공신고·인가완료 단지: 0건
- 공덕역 e·seo: 팀에서 만든 50건(`eseo_50_values.sql`)을 그대로 쓴다. description 에 원문 · 공간 유형 · 근본 요구 · 근본 요구 코드 · 표면 요청이 JSON 으로 들어 있고, 화면에는 원문만 보이며 관리자 챗봇 엑셀 내보내기에만 나머지 항목이 들어간다
- 최근 180일 사이, 오래된 민원일수록 처리완료 비율이 높다
- tracking_id 가 `DM-` 로 시작한다
- 부서는 V19 에서 늘어난 7개(승강기 · 전기·통신 · 소방·가스 · 청소·위생 포함)에 나눠 넣고, 처리완료 건은 처리 시각(resolved_at)도 채운다
- **백엔드를 V19 까지 올린 뒤에 넣어야 한다** (resolved_at 컬럼·새 부서 필요)

```bash
# 넣기 (다시 실행하면 지우고 새로 넣는다)
docker exec -i dasigochim-postgres-1 psql -U dasigochim -d dasigochim < scripts/dummy-reports/dummy_reports.sql

# 지우기
docker exec -i dasigochim-postgres-1 psql -U dasigochim -d dasigochim < scripts/dummy-reports/clear_dummy_reports.sql

# 유형·비율을 바꿨으면 다시 생성
python3 scripts/dummy-reports/generate_dummy_reports.py > scripts/dummy-reports/dummy_reports.sql
```
