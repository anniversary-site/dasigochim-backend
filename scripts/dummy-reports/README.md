# 민원 더미데이터 (로컬·시연용)

청년주택마다 민원이 많은 곳 · 보통인 곳 · 적은 곳 · 없는 곳이 섞이도록 만든 더미 민원.
Flyway 마이그레이션이 아니라서 운영 DB 에는 자동으로 들어가지 않는다.

- 입주 단지: 많음(18~35건) 15% · 보통(6~14건) 35% · 적음(1~4건) 35% · 없음 15%
- 준공 단지: 0~3건, 착공신고·인가완료 단지: 0건
- 공덕역 e·seo: 46건, 공용공간·세대 민원에 3D 핀 위치 포함
- 최근 180일 사이, 오래된 민원일수록 처리완료 비율이 높다
- tracking_id 가 `DM-` 로 시작한다

```bash
# 넣기 (다시 실행하면 지우고 새로 넣는다)
docker exec -i dasigochim-postgres-1 psql -U dasigochim -d dasigochim < scripts/dummy-reports/dummy_reports.sql

# 지우기
docker exec -i dasigochim-postgres-1 psql -U dasigochim -d dasigochim < scripts/dummy-reports/clear_dummy_reports.sql

# 유형·비율을 바꿨으면 다시 생성
python3 scripts/dummy-reports/generate_dummy_reports.py > scripts/dummy-reports/dummy_reports.sql
```
