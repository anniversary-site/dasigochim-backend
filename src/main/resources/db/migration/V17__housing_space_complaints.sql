-- 캠퍼스 안전신고 → 청년주택 3D 공간 민원으로 전환한다.
--
-- buildings 는 이제 "청년주택 단지" 한 곳을 뜻한다. 신고 위치는 지도 좌표 대신
-- 단지 안의 공간(세대 내부 / 공용공간)과 그 공간 3D 장면 위에 찍은 핀 좌표로 남긴다.

-- ── 1. 캠퍼스 시드 정리 ──────────────────────────────────────────────
-- V12 가 넣은 과거사례(캠퍼스 신고)를 지운다. 사진·로그·인사이트·임베딩은 CASCADE.
DELETE FROM reports
WHERE reporter_id = (SELECT id FROM users WHERE provider_id = 'system-seed');

-- 신고가 걸려 있지 않은 캠퍼스 건물을 지운다.
DELETE FROM buildings b
WHERE NOT EXISTS (SELECT 1 FROM reports r WHERE r.building_id = b.id);

-- ── 2. 청년주택 단지 ─────────────────────────────────────────────────
ALTER TABLE buildings
    ADD COLUMN sido    VARCHAR(30),
    ADD COLUMN sigungu VARCHAR(30);

-- 좌표는 지도 기능을 쓰지 않으므로 비워 둔다 (V9 에서 nullable).
INSERT INTO buildings (name, code, lat, lng, address, sido, sigungu)
VALUES ('공덕역 청년안심주택 e·seo(이서)', 'ESEO314', NULL, NULL,
        '서울특별시 마포구 염리동 172-10', '서울특별시', '마포구');

-- ── 3. 호수 → 세대 타입 ──────────────────────────────────────────────
-- 공개 자료에는 호수별 타입 표가 없다(호수는 추첨 배정). 아래는 층별 호수 배치도의
-- 위치를 보고 만든 **임시 표**다. 각 층 서쪽 날개 왼쪽 열 위에서부터 01호로 매기고
-- 오른쪽 열 → 모서리 세대 → 남쪽 줄(서→동) → 북쪽 19 TYPE 순으로 번호를 붙였다.
-- 실제 표를 받으면 이 테이블만 갈아 끼우면 된다.
CREATE TABLE building_units (
    id          BIGSERIAL PRIMARY KEY,
    building_id BIGINT      NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    dong        VARCHAR(10),
    ho          VARCHAR(10) NOT NULL,
    floor       VARCHAR(10) NOT NULL,
    unit_type   VARCHAR(10) NOT NULL,
    scene_id    VARCHAR(40) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_building_units ON building_units (building_id, COALESCE(dong, ''), ho);

WITH layout(idx, unit_type) AS (
    VALUES (1, '42'), (2, '42'), (3, '42'), (4, '42'), (5, '42'), (6, '49'), (7, '18B'),
           (8, '18A'), (9, '18A'), (10, '18A'), (11, '18A'), (12, '18A'), (13, '18A'), (14, '18A'), (15, '18A'),
           (16, '19'), (17, '19')
),
typical AS (
    -- 3층은 같은 자리가 52·64 TYPE 이다.
    SELECT f AS floor, l.idx,
           CASE WHEN f = 3 AND l.unit_type = '42' THEN '52'
                WHEN f = 3 AND l.unit_type = '49' THEN '64'
                ELSE l.unit_type END AS unit_type
    FROM generate_series(3, 19) AS f, layout l
),
top_floor AS (
    SELECT 20 AS floor, idx, unit_type
    FROM (VALUES (1, '37'), (2, '18A'), (3, '18A'), (4, '18A'), (5, '18A'), (6, '18A'), (7, '18A'), (8, '18A'),
                 (9, '19'), (10, '19')) AS t(idx, unit_type)
),
all_units AS (
    SELECT * FROM typical UNION ALL SELECT * FROM top_floor
)
INSERT INTO building_units (building_id, dong, ho, floor, unit_type, scene_id)
SELECT b.id, NULL, (u.floor * 100 + u.idx)::text, u.floor::text, u.unit_type,
       CASE u.unit_type
           WHEN '18A' THEN 'unit_18A-type'
           WHEN '18B' THEN 'unit_18B-type'
           WHEN '19'  THEN 'unit_19-type'
           WHEN '37'  THEN 'unit_37-type'
           WHEN '42'  THEN 'unit_42-type'
           WHEN '49'  THEN 'unit_49-type'
           ELSE 'floor_3F'  -- 52·64 TYPE 은 세대 평면도가 없어 층 배치도로 대신한다.
       END
FROM all_units u
JOIN buildings b ON b.code = 'ESEO314';

-- ── 4. 공용공간 ──────────────────────────────────────────────────────
CREATE TABLE building_spaces (
    id             BIGSERIAL PRIMARY KEY,
    building_id    BIGINT      NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    code           VARCHAR(30) NOT NULL,
    name           VARCHAR(60) NOT NULL,
    floor_label    VARCHAR(20),
    scene_id       VARCHAR(40) NOT NULL,
    requires_floor BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order     INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_building_spaces UNIQUE (building_id, code)
);

-- 상가(근린생활시설)는 입주민 공간이 아니라 넣지 않는다.
INSERT INTO building_spaces (building_id, code, name, floor_label, scene_id, requires_floor, sort_order)
SELECT b.id, s.code, s.name, s.floor_label, s.scene_id, s.requires_floor, s.sort_order
FROM buildings b
JOIN (VALUES
    ('PARKING',        '지하 주차장',                 'B1',    'neighborhood_B1F', FALSE, 1),
    ('LOBBY',          '로비 라운지',                 '1F',    'neighborhood_1F',  FALSE, 2),
    ('FITNESS',        '피트니스',                   '2F',    'neighborhood_2F',  FALSE, 3),
    ('COMMUNITY',      '휴게·커뮤니티 공간',          '2F',    'neighborhood_2F',  FALSE, 4),
    ('LAUNDRY',        '공동세탁실',                  '2F',    'neighborhood_2F',  FALSE, 5),
    ('ROOFTOP',        '옥상정원',                   '20F',   'floor_20F',        FALSE, 6),
    ('CORRIDOR_3F',    '복도·엘리베이터 홀 (3층)',     '3F',    'floor_3F',         FALSE, 7),
    ('CORRIDOR_4_18F', '복도·엘리베이터 홀 (4~18층)',  '4~18F', 'floor_4-18F',      TRUE,  8),
    ('CORRIDOR_19F',   '복도·엘리베이터 홀 (19층)',    '19F',   'floor_19F',        FALSE, 9),
    ('CORRIDOR_20F',   '복도·엘리베이터 홀 (20층)',    '20F',   'floor_20F',        FALSE, 10)
) AS s(code, name, floor_label, scene_id, requires_floor, sort_order) ON TRUE
WHERE b.code = 'ESEO314';

-- ── 5. 신고 위치: 공간 + 3D 핀 ───────────────────────────────────────
ALTER TABLE reports
    ADD COLUMN space_type VARCHAR(10),
    ADD COLUMN space_code VARCHAR(30),
    ADD COLUMN space_name VARCHAR(60),
    ADD COLUMN dong       VARCHAR(10),
    ADD COLUMN unit_type  VARCHAR(10),
    ADD COLUMN scene_id   VARCHAR(40),
    ADD COLUMN pin_x      DOUBLE PRECISION,
    ADD COLUMN pin_y      DOUBLE PRECISION,
    ADD COLUMN pin_z      DOUBLE PRECISION,
    ADD COLUMN pin_label  VARCHAR(60);

-- ── 6. 입주민 프로필 ─────────────────────────────────────────────────
-- 학과·학번 대신 거주 단지와 동·호수를 받는다. 기존 컬럼은 남겨 둔다.
ALTER TABLE users
    ADD COLUMN building_id BIGINT REFERENCES buildings(id),
    ADD COLUMN dong        VARCHAR(10),
    ADD COLUMN ho          VARCHAR(10);

-- ── 7. 담당 부서: 주택 민원용 ─────────────────────────────────────────
-- 코드(FACILITY 등)는 관리자 계정·AI 판정 결과가 참조하므로 그대로 두고 이름과 업무범위만 바꾼다.
UPDATE departments SET name = '시설·하자',     contact = NULL WHERE code = 'FACILITY';
UPDATE departments SET name = '주차·보안',     contact = NULL WHERE code = 'SAFETY_CENTER';
UPDATE departments SET name = '생활·공용공간', contact = NULL WHERE code = 'GENERAL_AFFAIRS';

DELETE FROM department_duties;
UPDATE department_duty_corpus SET version = 2 WHERE id = 1;

-- ai-server/app/backends/fixtures/departments.json 과 문장이 동일해야 한다.
INSERT INTO department_duties (department_id, duty_text)
SELECT d.id, t.duty_text
FROM departments d
JOIN (VALUES
    ('FACILITY', '세대 내부 바닥·벽·천장 마감재 파손, 들뜸, 곰팡이, 결로 하자의 점검과 보수를 담당한다.'),
    ('FACILITY', '세대와 공용부의 누수, 배관 막힘·파열, 수압·온수 불량 등 급배수 설비 하자를 담당한다.'),
    ('FACILITY', '조명, 콘센트, 차단기, 인터폰, 도어락 등 전기·통신 설비의 고장과 교체를 담당한다.'),
    ('FACILITY', '빌트인 에어컨, 냉장고, 세탁기, 가스쿡탑, 전열교환기 등 기본 옵션 가전의 고장 접수와 수리를 담당한다.'),
    ('FACILITY', '승강기, 창호, 출입문, 난간 등 건축 설비의 고장과 파손 보수를 담당한다.'),
    ('SAFETY_CENTER', '지하 주차장의 주차 질서, 무단 주차, 차단기와 주차 유도 시설의 운영을 담당한다.'),
    ('SAFETY_CENTER', '공동현관 출입 통제, 출입 권한, 외부인 출입 등 단지 보안 관리를 담당한다.'),
    ('SAFETY_CENTER', 'CCTV, 비상벨 등 방범 설비의 점검과 영상 확인 요청을 담당한다.'),
    ('SAFETY_CENTER', '소화기·소화전·비상구·피난 유도등 등 소방시설의 미비와 비상통로 적치물을 담당한다.'),
    ('SAFETY_CENTER', '단지 내 안전사고 발생 시 현장 확인과 재발방지 조치를 담당한다.'),
    ('GENERAL_AFFAIRS', '복도, 로비, 계단 등 공용공간의 청소·미화와 악취 관리를 담당한다.'),
    ('GENERAL_AFFAIRS', '분리수거장 관리, 무단 투기·방치 폐기물과 대형 폐기물 수거를 담당한다.'),
    ('GENERAL_AFFAIRS', '층간·벽간 소음, 흡연, 반려동물 등 입주민 간 생활 불편 민원의 조정을 담당한다.'),
    ('GENERAL_AFFAIRS', '피트니스, 공동세탁실, 커뮤니티 공간, 옥상정원 등 주민공동시설의 운영과 비품 관리를 담당한다.'),
    ('GENERAL_AFFAIRS', '공용공간 게시물, 안내 표지판, 택배 보관 등 생활 편의 운영을 담당한다.')
) AS t(code, duty_text) ON t.code = d.code;

-- ── 8. 과거사례: 주택 민원 ───────────────────────────────────────────
-- C-5 과거사례 검색이 돌려줄 예시 8건. ai-server/app/backends/fixtures/cases.json 과 1:1 로 대응한다.
WITH seed(days_ago, floor, space_type, space_name, status, risk_level, hazard, photo_analysis,
          risk, action_fundamental, department_code) AS (
    VALUES
    (6,   '12', 'UNIT',   '세대 욕실',     'RESOLVED',  'MEDIUM',
     '12층 세대 욕실 천장 누수 재발',
     '욕실 천장 점검구 주변에 물자국이 번지고 물방울이 떨어짐.',
     '젖은 천장재가 처지면 탈락하거나 조명 누전으로 이어질 수 있음.',
     '윗층 세대 욕실 배수관 이음부를 교체하고 천장재를 재시공함.', 'FACILITY'),
    (14,  'B1', 'COMMON', '지하 주차장',   'REVIEWING', 'MEDIUM',
     '지하 주차장 조명 소등 구간 발생',
     '지하 1층 주차 구역 조명 일부가 꺼져 통로가 어두움.',
     '보행자 인지가 늦어 차량과 접촉 사고가 날 수 있음.',
     '', 'SAFETY_CENTER'),
    (21,  '2',  'COMMON', '피트니스',     'RESOLVED',  'LOW',
     '피트니스 러닝머신 벨트 파손',
     '러닝머신 1대의 벨트가 찢어져 작동 중 멈춤 현상이 있음.',
     '사용 중 급정지로 넘어질 수 있음.',
     '벨트를 교체하고 월 1회 운동기구 점검표를 비치함.', 'GENERAL_AFFAIRS'),
    (30,  '2',  'COMMON', '공동세탁실',    'RESOLVED',  'MEDIUM',
     '공동세탁실 배수구 역류',
     '세탁기 배수구에서 물이 역류해 바닥이 젖어 있음.',
     '젖은 바닥에서 미끄러지거나 누전 사고가 날 수 있음.',
     '배수 트랩을 청소하고 배수구 거름망을 설치함.', 'FACILITY'),
    (45,  '7',  'COMMON', '복도',         'RESOLVED',  'LOW',
     '7층 복도 대형 폐기물 방치',
     '복도 끝 비상계단 앞에 매트리스와 박스가 쌓여 있음.',
     '피난 동선을 막아 화재 시 대피가 늦어질 수 있음.',
     '대형 폐기물 배출 절차를 공지하고 방치 물품을 수거함.', 'GENERAL_AFFAIRS'),
    (58,  '1',  'COMMON', '로비 라운지',   'RESOLVED',  'HIGH',
     '공동현관 출입문 잠금 불량',
     '공동현관 자동문이 닫힌 뒤 잠기지 않아 외부인이 그대로 들어올 수 있음.',
     '외부인 무단 출입으로 범죄 피해가 생길 수 있음.',
     '도어 클로저와 전기 잠금장치를 교체함.', 'SAFETY_CENTER'),
    (77,  '15', 'UNIT',   '세대 거실',     'RESOLVED',  'MEDIUM',
     '15층 세대 빌트인 에어컨 물샘',
     '빌트인 에어컨 실내기 아래로 물이 떨어져 바닥이 젖음.',
     '젖은 바닥에서 미끄러지거나 가구가 손상될 수 있음.',
     '배수 호스 막힘을 뚫고 배수 기울기를 다시 잡음.', 'FACILITY'),
    (102, '20', 'COMMON', '옥상정원',      'RESOLVED',  'LOW',
     '옥상정원 목재 데크 들뜸',
     '옥상정원 목재 데크 일부가 들떠 틈이 벌어져 있음.',
     '발이 걸려 넘어질 수 있음.',
     '들뜬 데크재를 교체하고 고정 나사를 보강함.', 'FACILITY')
),
resolved AS (
    SELECT s.*,
           (now() - make_interval(days => s.days_ago)) AS submitted_at,
           b.id   AS building_id,
           b.name AS building_name,
           d.id   AS department_id,
           (SELECT id FROM users WHERE provider_id = 'system-seed') AS reporter_id
    FROM seed s
    JOIN buildings b ON b.code = 'ESEO314'
    LEFT JOIN departments d ON d.code = s.department_code
),
inserted AS (
    INSERT INTO reports (tracking_id, summary, description, status, risk_level, risk_level_source,
                         department_id, reporter_id, reporter_type, building_id, building_name_snapshot,
                         is_indoor, floor, space_type, space_name, location_description,
                         detected_hazards, submitted_at, created_at, updated_at)
    SELECT 'R-' || to_char(r.submitted_at, 'YYYY-MMDD'),
           r.hazard,
           r.photo_analysis || ' ' || r.risk,
           r.status,
           r.risk_level,
           'AI',
           r.department_id,
           r.reporter_id,
           'ANONYMOUS',
           r.building_id,
           r.building_name,
           TRUE,
           r.floor,
           r.space_type,
           r.space_name,
           r.space_name,
           r.hazard,
           r.submitted_at,
           r.submitted_at,
           r.submitted_at
    FROM resolved r
    RETURNING id, tracking_id, status, submitted_at
)
INSERT INTO report_activity_logs (report_id, activity_type, from_status, to_status,
                                  actor_type, actor_id, action_note, created_at)
SELECT i.id, 'RESOLVE', 'REVIEWING', 'RESOLVED', 'SYSTEM', NULL, r.action_fundamental, i.submitted_at
FROM inserted i
JOIN resolved r ON 'R-' || to_char(r.submitted_at, 'YYYY-MMDD') = i.tracking_id
WHERE i.status = 'RESOLVED' AND r.action_fundamental <> '';
