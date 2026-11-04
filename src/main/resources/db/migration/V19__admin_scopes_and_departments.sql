-- 관리자 담당 범위와 부서 확장.
--
-- 1) 관리자 역할: SUPER(전체) / REGION(시·도 또는 시·군·구) / BUILDING(특정 청년주택).
--    기존 관리자는 SUPER 로 둔다 — 지금까지처럼 전부 본다.
-- 2) admin_scopes: 관리자가 맡은 구역. 한 사람이 여러 구역을 맡을 수 있다.
--    REGION 은 sido (+ sigungu), BUILDING 은 building_id 를 채운다.
-- 3) 부서 4개 추가 (승강기 · 전기·통신 · 소방·가스 · 청소·위생) 와 업무범위 문장 재배치.
--    AI 부서 분류(C-1)는 department_duties 문장으로 후보를 고르므로 문장을 옮기면 분류도 따라온다.

ALTER TABLE admins ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'SUPER';

CREATE TABLE admin_scopes (
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT       NOT NULL REFERENCES admins(id) ON DELETE CASCADE,
    sido        VARCHAR(30),
    sigungu     VARCHAR(30),
    building_id BIGINT       REFERENCES buildings(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_admin_scopes_target CHECK (building_id IS NOT NULL OR sido IS NOT NULL)
);
CREATE INDEX idx_admin_scopes_admin ON admin_scopes (admin_id);

INSERT INTO departments (name, code)
SELECT v.name, v.code
FROM (VALUES ('승강기', 'ELEVATOR'),
             ('전기·통신', 'ELECTRIC'),
             ('소방·가스', 'FIRE_GAS'),
             ('청소·위생', 'SANITATION')) AS v(name, code)
WHERE NOT EXISTS (SELECT 1 FROM departments d WHERE d.code = v.code);

-- 승강기를 시설 문장에서 떼어 승강기 부서로
UPDATE department_duties
SET duty_text = '창호, 출입문, 난간 등 건축 설비의 고장과 파손 보수를 담당한다.'
WHERE duty_text = '승강기, 창호, 출입문, 난간 등 건축 설비의 고장과 파손 보수를 담당한다.';

INSERT INTO department_duties (department_id, duty_text)
SELECT d.id, v.text
FROM departments d
JOIN (VALUES
    ('ELEVATOR', '승강기 고장, 갇힘, 문 닫힘 이상, 운행 중 소음·진동과 정기검사를 담당한다.'),
    ('FIRE_GAS', '가스 냄새·누출, 가스 차단기, 화재감지기·스프링클러 오작동 등 화재·가스 안전을 담당한다.'),
    ('SANITATION', '바퀴벌레·해충 방역, 소독, 음식물 쓰레기 악취 등 위생 관리를 담당한다.')
) AS v(code, text) ON d.code = v.code
WHERE NOT EXISTS (SELECT 1 FROM department_duties x WHERE x.duty_text = v.text);

UPDATE department_duties SET department_id = (SELECT id FROM departments WHERE code = 'ELECTRIC')
WHERE duty_text LIKE '조명, 콘센트, 차단기, 인터폰, 도어락%';

UPDATE department_duties SET department_id = (SELECT id FROM departments WHERE code = 'FIRE_GAS')
WHERE duty_text LIKE '소화기·소화전·비상구·피난 유도등%';

UPDATE department_duties SET department_id = (SELECT id FROM departments WHERE code = 'SANITATION')
WHERE duty_text LIKE '분리수거장 관리%' OR duty_text LIKE '복도, 로비, 계단 등 공용공간의 청소%';

UPDATE department_duty_corpus SET version = version + 1 WHERE id = 1;

-- 4) 처리완료 시각 — 대시보드 평균 처리 기간에 쓴다. 기존 건은 처리 이력에서 채운다.
ALTER TABLE reports ADD COLUMN resolved_at TIMESTAMPTZ;
UPDATE reports r SET resolved_at = l.created_at
FROM (SELECT report_id, MAX(created_at) AS created_at
      FROM report_activity_logs WHERE to_status = 'RESOLVED' GROUP BY report_id) l
WHERE r.id = l.report_id AND r.status = 'RESOLVED';
UPDATE reports SET resolved_at = updated_at WHERE status = 'RESOLVED' AND resolved_at IS NULL;
