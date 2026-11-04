#!/usr/bin/env python3
"""청년주택 민원 더미데이터 SQL 생성기 (로컬·시연용).

    python3 scripts/dummy-reports/generate_dummy_reports.py > scripts/dummy-reports/dummy_reports.sql

- buildings.json : 단지 목록 (이름 · 시군구 · 공급단계 · 3D 여부). 단지 id 대신 이름으로 찾는다.
- eseo_units.json: 공덕역 e·seo 세대 표 중 세대 3D 평면이 있는 세대.
- 시드를 고정해 몇 번을 돌려도 같은 SQL 이 나온다.
- 만든 민원은 tracking_id 가 'DM-' 로 시작한다. clear_dummy_reports.sql 로 지운다.
- Flyway 마이그레이션이 아니다. 운영 DB 에 자동으로 들어가지 않는다.
"""
import json
import random
from datetime import datetime, timedelta, timezone
from pathlib import Path

HERE = Path(__file__).parent
RNG = random.Random(314)
NOW = datetime(2026, 11, 4, 12, 0, tzinfo=timezone(timedelta(hours=9)))

buildings = json.loads((HERE / "buildings.json").read_text())
eseo_units = json.loads((HERE / "eseo_units.json").read_text())

# ── 민원 유형 ─────────────────────────────────────────────────────────────
# (제목, 설명, 위험도, 부서)
COMMON = {
    "지하 주차장": ("B1", [
        ("조명 소등 구간 발생", "주차 구역 조명 몇 개가 꺼져 있어 밤에 차량과 보행자가 잘 안 보여요.", "MEDIUM", "SAFETY_CENTER"),
        ("차량 진입 차단기 오작동", "출입 차단기가 등록 차량인데도 안 열리거나 늦게 열려서 뒤에 차가 밀려요.", "MEDIUM", "SAFETY_CENTER"),
        ("바닥 누수·물고임", "비 온 뒤 주차장 바닥에 물이 고여 미끄럽고 천장에서 물이 떨어져요.", "MEDIUM", "FACILITY"),
        ("안전봉 파손", "주차장 기둥 옆 안전봉이 부러져 날카로운 부분이 튀어나와 있어요.", "HIGH", "FACILITY"),
        ("외부 차량 무단 주차", "입주민 전용 구역에 외부 차량이 며칠째 세워져 있어요.", "LOW", "SAFETY_CENTER"),
    ]),
    "엘리베이터": (None, [
        ("엘리베이터 문 닫힘 지연", "문이 닫히다가 다시 열리는 일이 반복돼서 오래 기다려요.", "MEDIUM", "FACILITY"),
        ("엘리베이터 이상 소음", "운행할 때 덜컹거리는 소리와 진동이 심해요.", "HIGH", "FACILITY"),
        ("엘리베이터 버튼 고장", "몇몇 층 버튼이 눌러도 불이 안 들어와요.", "LOW", "FACILITY"),
        ("엘리베이터 갇힘 발생", "운행 중 멈춰서 몇 분 동안 갇혀 있었어요. 비상벨 응답도 늦었어요.", "HIGH", "FACILITY"),
    ]),
    "복도·엘리베이터 홀": (None, [
        ("복도 조명 깜빡임", "복도 센서등이 계속 깜빡이거나 안 켜져요.", "LOW", "FACILITY"),
        ("복도 적치물 방치", "복도에 택배 상자와 개인 물품이 쌓여 있어 지나가기 불편해요.", "LOW", "GENERAL_AFFAIRS"),
        ("소화기 위치 가림", "소화기함 앞에 짐이 쌓여 있어 바로 꺼낼 수 없어요.", "HIGH", "SAFETY_CENTER"),
        ("복도 담배 냄새 유입", "복도에서 담배 냄새가 계속 나요. 계단실에서 피우는 것 같아요.", "LOW", "GENERAL_AFFAIRS"),
    ]),
    "공동현관": ("1", [
        ("공동현관 출입문 잠금 불량", "공동현관 문이 제대로 안 닫혀서 외부인이 그냥 들어올 수 있어요.", "HIGH", "SAFETY_CENTER"),
        ("공동현관 인터폰 고장", "방문자 호출이 세대로 연결되지 않아요.", "MEDIUM", "FACILITY"),
        ("출입카드 인식 불량", "카드를 여러 번 대야 겨우 문이 열려요.", "LOW", "SAFETY_CENTER"),
    ]),
    "분리수거장": ("1", [
        ("분리수거장 악취", "음식물 쓰레기통이 넘쳐 악취가 심하고 벌레가 꼬여요.", "LOW", "GENERAL_AFFAIRS"),
        ("분리수거 수거 지연", "재활용 수거가 며칠째 안 돼서 수거장이 꽉 찼어요.", "LOW", "GENERAL_AFFAIRS"),
        ("대형 폐기물 무단 투기", "스티커 없이 버려진 가구가 통로를 막고 있어요.", "LOW", "GENERAL_AFFAIRS"),
    ]),
    "택배보관함": ("1", [
        ("무인 택배함 오류", "택배함 화면이 멈춰서 물건을 못 꺼내요.", "LOW", "GENERAL_AFFAIRS"),
        ("택배 분실", "택배함에 보관됐다고 떴는데 열어 보니 비어 있었어요.", "MEDIUM", "SAFETY_CENTER"),
    ]),
    "커뮤니티 공간": ("2", [
        ("커뮤니티실 냉난방 불량", "에어컨이 약하게 나와서 여름에 쓰기 어려워요.", "LOW", "FACILITY"),
        ("커뮤니티실 예약 문제", "예약 시스템에서 예약이 겹쳐서 이용을 못 했어요.", "LOW", "GENERAL_AFFAIRS"),
        ("공용 가구 파손", "의자 다리가 흔들려서 앉으면 넘어질 것 같아요.", "MEDIUM", "FACILITY"),
    ]),
    "피트니스": ("2", [
        ("러닝머신 벨트 파손", "러닝머신 벨트가 찢어져 달리다 걸릴 것 같아요.", "MEDIUM", "FACILITY"),
        ("운동기구 고장", "웨이트 기구 케이블이 끊어져 사용을 못 해요.", "MEDIUM", "FACILITY"),
        ("피트니스 환기 불량", "환기가 안 돼서 운동하면 숨이 막히고 냄새가 나요.", "LOW", "FACILITY"),
    ]),
    "공동세탁실": ("2", [
        ("세탁기 고장", "세탁기 한 대가 돌아가다 멈추고 에러가 떠요.", "LOW", "FACILITY"),
        ("세탁실 배수구 역류", "세탁실 바닥 배수구에서 물이 역류해 바닥이 젖어 있어요.", "MEDIUM", "FACILITY"),
        ("건조기 과열 냄새", "건조기를 돌리면 타는 냄새가 나요.", "HIGH", "FACILITY"),
    ]),
    "옥상정원": ("R", [
        ("옥상 데크 들뜸", "목재 데크가 들떠서 걸려 넘어질 뻔했어요.", "LOW", "FACILITY"),
        ("옥상 출입문 개방 방치", "옥상 출입문이 밤에도 열려 있어요.", "MEDIUM", "SAFETY_CENTER"),
        ("옥상 난간 흔들림", "옥상 난간 일부가 손으로 밀면 흔들려요.", "HIGH", "FACILITY"),
    ]),
}

UNIT = [
    ("욕실 천장 누수", "윗집 쪽에서 물이 새는지 욕실 천장에서 물이 떨어져요.", "MEDIUM", "FACILITY", ["욕실"]),
    ("결로·곰팡이 발생", "창문 주변과 벽 모서리에 결로가 생기고 곰팡이가 번지고 있어요.", "MEDIUM", "FACILITY", ["방", "침실", "거실"]),
    ("보일러 온수 불량", "온수가 미지근하게 나오거나 중간에 찬물로 바뀌어요.", "MEDIUM", "FACILITY", ["욕실", "주방"]),
    ("빌트인 에어컨 물샘", "에어컨을 켜면 실내기에서 물이 떨어져요.", "MEDIUM", "FACILITY", ["방", "거실", "침실"]),
    ("현관 도어락 오작동", "도어락이 비밀번호를 맞게 눌러도 안 열릴 때가 있어요.", "HIGH", "SAFETY_CENTER", ["현관"]),
    ("주방 수전 누수", "싱크대 수전 아래에서 물이 조금씩 새요.", "LOW", "FACILITY", ["주방"]),
    ("방충망 파손", "방충망이 찢어져서 벌레가 들어와요.", "LOW", "FACILITY", ["방", "침실", "거실"]),
    ("층간소음", "밤늦게 윗집에서 쿵쿵 소리가 계속 나요.", "LOW", "GENERAL_AFFAIRS", ["방", "침실", "거실"]),
    ("배수구 악취", "욕실 배수구에서 하수구 냄새가 올라와요.", "LOW", "FACILITY", ["욕실"]),
    ("전기 콘센트 스파크", "콘센트에 플러그를 꽂을 때 불꽃이 튀어요.", "HIGH", "FACILITY", ["방", "주방", "거실"]),
]

OUTDOOR = [
    ("단지 앞 보도", "보도블록 파손", "단지 앞 보도블록이 깨져서 걸려 넘어질 뻔했어요.", "MEDIUM", "FACILITY"),
    ("단지 앞 보도", "가로등 고장", "단지 앞 가로등이 꺼져 있어 밤길이 어두워요.", "MEDIUM", "SAFETY_CENTER"),
    ("단지 출입구", "출입구 불법 주정차", "단지 출입구 앞에 차가 세워져 있어 드나들기 위험해요.", "MEDIUM", "SAFETY_CENTER"),
    ("단지 외곽 화단", "흡연 장소화", "건물 옆 화단에서 사람들이 담배를 피워 연기가 창문으로 들어와요.", "LOW", "GENERAL_AFFAIRS"),
    ("단지 앞 횡단보도", "횡단보도 신호 짧음", "단지 앞 횡단보도 신호가 짧아서 건너기 빠듯해요.", "LOW", "GENERAL_AFFAIRS"),
]

# ── 공덕역 e·seo: 3D 장면과 핀 위치 ────────────────────────────────────────
# 장면 라벨 위치(도면 픽셀) → 장면 좌표 (x=(px-cx)/ppm, z=(py-cy)/ppm). 지터를 줘서 퍼뜨린다.
def P(px, py, cx, cy, ppm):
    return round((px - cx) / ppm, 3), round((py - cy) / ppm, 3)

ESEO_COMMON = {
    # 공간 코드: (공간 이름, 층, 장면, [(라벨, x, z)], 퍼짐(m), 민원 유형 묶음)
    "PARKING": ("지하 주차장", "B1", "neighborhood_B1F",
                [("주차장", *P(380, 520, 512, 368, 14.2)), ("램프", *P(560, 190, 512, 368, 14.2))], 2.0, "지하 주차장"),
    "LOBBY": ("로비 라운지", "1F", "neighborhood_1F",
              [("로비 라운지", *P(520, 300, 572, 436, 15)), ("공용 화장실", *P(705, 400, 572, 436, 15))], 1.2, "공동현관"),
    "FITNESS": ("피트니스", "2F", "neighborhood_2F",
                [("피트니스", *P(470, 200, 554.5, 387.5, 15))], 1.0, "피트니스"),
    "COMMUNITY": ("휴게·커뮤니티 공간", "2F", "neighborhood_2F",
                  [("휴게 및 커뮤니티공간", *P(548, 300, 554.5, 387.5, 15))], 1.0, "커뮤니티 공간"),
    "LAUNDRY": ("공동세탁실", "2F", "neighborhood_2F",
                [("공동세탁실", *P(465, 330, 554.5, 387.5, 15))], 0.6, "공동세탁실"),
    "ROOFTOP": ("옥상정원", "20F", "floor_20F",
                [("옥상정원", *P(420, 300, 606, 407, 14.19))], 1.5, "옥상정원"),
    "CORRIDOR_4_18F": ("복도·엘리베이터 홀 (4~18층)", "4~18F", "floor_4-18F",
                       [("복도", 0.0, 0.0)], 1.2, "복도·엘리베이터 홀"),
    "CORRIDOR_19F": ("복도·엘리베이터 홀 (19층)", "19F", "floor_19F",
                     [("복도", 0.0, 0.0)], 1.2, "복도·엘리베이터 홀"),
}
ESEO_COMMON_WEIGHTS = {"PARKING": 6, "LOBBY": 3, "FITNESS": 3, "COMMUNITY": 2, "LAUNDRY": 4,
                       "ROOFTOP": 2, "CORRIDOR_4_18F": 5, "CORRIDOR_19F": 1}

UNIT_SCENES = {  # 세대 장면: (cx, cy, ppm, {라벨: (px, py)})
    "unit_18A-type": (120, 286, 75, {"현관": (150, 90), "욕실": (55, 210), "주방": (200, 280), "방": (110, 420)}),
    "unit_18B-type": (120, 298, 75, {"현관": (165, 115), "욕실": (55, 230), "주방": (198, 300), "방": (110, 400)}),
    "unit_19-type": (118, 298, 75, {"현관": (93, 110), "욕실": (180, 230), "주방": (42, 300), "방": (135, 400)}),
    "unit_37-type": (201, 225, 66, {"욕실": (60, 60), "주방": (330, 120), "거실": (300, 330), "침실": (90, 330)}),
    "unit_42-type": (190, 231, 60, {"주방": (60, 120), "거실": (100, 330), "욕실": (237, 110), "침실": (290, 300)}),
    "unit_49-type": (250, 268, 56, {"침실 1": (80, 360), "거실": (250, 380), "주방": (280, 240), "욕실": (75, 120)}),
}


def unit_room_pin(scene, wanted):
    cx, cy, ppm, rooms = UNIT_SCENES[scene]
    names = [r for r in rooms if any(r.startswith(w) for w in wanted)] or list(rooms)
    room = RNG.choice(names)
    x, z = P(*rooms[room], cx, cy, ppm)
    return room, round(x + RNG.uniform(-0.25, 0.25), 3), round(z + RNG.uniform(-0.25, 0.25), 3)


# ── 단지별 민원 규모 ───────────────────────────────────────────────────────
def volume(b):
    if b["has3d"]:
        return 46
    if b["status"] == "입주":
        tier = RNG.choices(["많음", "보통", "적음", "없음"], weights=[15, 35, 35, 15])[0]
        return {"많음": RNG.randint(18, 35), "보통": RNG.randint(6, 14),
                "적음": RNG.randint(1, 4), "없음": 0}[tier]
    if b["status"] == "준공":
        return RNG.choice([0, 0, 1, 2, 3])
    return 0  # 착공신고 · 인가완료: 아직 사람이 안 산다


def when():
    # 최근일수록 많이 들어오게: 0~180일 전
    days = int(RNG.triangular(0, 180, 10))
    return NOW - timedelta(days=days, hours=RNG.randint(0, 23), minutes=RNG.randint(0, 59)), days


def status_for(days):
    if days > 45:
        return RNG.choices(["RESOLVED", "REVIEWING"], weights=[92, 8])[0]
    if days > 14:
        return RNG.choices(["RESOLVED", "REVIEWING", "RECEIVED"], weights=[60, 28, 12])[0]
    return RNG.choices(["RESOLVED", "REVIEWING", "RECEIVED"], weights=[15, 35, 50])[0]


def q(value):
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)):
        return repr(value)
    return "'" + str(value).replace("'", "''") + "'"


rows = []
seq = 0
for b in buildings:
    for _ in range(volume(b)):
        seq += 1
        at, days = when()
        kind = RNG.choices(["COMMON", "UNIT", "OUTDOOR"], weights=[55, 35, 10])[0]
        r = dict(building=b["name"], indoor=True, floor=None, room=None, space_type=None, space_code=None,
                 space_name=None, unit_type=None, scene_id=None, pin=None, pin_label=None)
        if kind == "UNIT":
            title, desc, risk, dept, wanted = RNG.choice(UNIT)
            if b["has3d"]:
                u = RNG.choice(eseo_units)
                room, x, z = unit_room_pin(u["scene"], wanted)
                r.update(floor=u["floor"], room=u["ho"], unit_type=u["type"], scene_id=u["scene"],
                         pin=(x, 0.02, z), pin_label=room)
                where = f"{u['ho']}호 ({u['type']} TYPE) · {room}"
            else:
                fl = RNG.randint(2, 15)
                ho = f"{fl}{RNG.randint(1, 12):02d}"
                room = RNG.choice(wanted)
                r.update(floor=str(fl), room=ho)
                where = f"{ho}호 · {room}"
            r.update(space_type="UNIT", space_name="세대 내부")
        elif kind == "COMMON":
            if b["has3d"]:
                code = RNG.choices(list(ESEO_COMMON_WEIGHTS), weights=list(ESEO_COMMON_WEIGHTS.values()))[0]
                name, fl, scene, spots, spread, group = ESEO_COMMON[code]
                title, desc, risk, dept = RNG.choice(COMMON[group][1])
                label, x, z = RNG.choice(spots)
                if code == "CORRIDOR_4_18F":
                    fl = str(RNG.randint(4, 18))
                r.update(floor=fl, space_code=code, space_name=name, scene_id=scene,
                         pin=(round(x + RNG.uniform(-spread, spread), 3), 0.02,
                              round(z + RNG.uniform(-spread, spread), 3)),
                         pin_label=label)
                where = name if label in (name, "복도") or label in name or name.endswith(label) else f"{name} · {label} 부근"
            else:
                name = RNG.choices(list(COMMON), weights=[5, 4, 4, 3, 3, 2, 2, 1, 2, 1])[0]
                fl, issues = COMMON[name]
                title, desc, risk, dept = RNG.choice(issues)
                if name == "복도·엘리베이터 홀":
                    fl = str(RNG.randint(2, 15))
                r.update(floor=fl, space_name=name)
                where = name if fl in (None, "1", "2", "R", "B1") else f"{fl}층 {name}"
            r.update(space_type="COMMON")
        else:
            place, title, desc, risk, dept = RNG.choice(OUTDOOR)
            r.update(indoor=False, space_name=None)
            where = place
        status = status_for(days)
        if RNG.random() < 0.12:  # AI 판단과 다르게 위험도가 한 단계 달라지는 경우
            risk = {"LOW": "MEDIUM", "MEDIUM": RNG.choice(["LOW", "HIGH"]), "HIGH": "MEDIUM"}[risk]
        r.update(seq=seq, at=at, status=status, risk=risk, dept=dept, title=title, desc=desc,
                 location=f"{b['name']} {where}"[:200])
        rows.append(r)

print("-- 생성: scripts/dummy-reports/generate_dummy_reports.py (직접 고치지 말 것)")
print(f"-- 민원 {len(rows)}건 · tracking_id 'DM-' 접두어 · 로컬·시연용, 운영 DB 에 넣지 말 것")
print("BEGIN;")
print("DELETE FROM reports WHERE tracking_id LIKE 'DM-%';")
print("INSERT INTO users (provider, provider_id, name)")
print("SELECT 'GOOGLE', 'dummy-reporter', '더미 입주민'")
print("WHERE NOT EXISTS (SELECT 1 FROM users WHERE provider = 'GOOGLE' AND provider_id = 'dummy-reporter');")
cols = ("tracking_id, summary, description, status, risk_level, risk_level_source, department_id, reporter_id, "
        "reporter_type, building_id, building_name_snapshot, is_indoor, floor, room, location_description, "
        "space_type, space_code, space_name, unit_type, scene_id, pin_x, pin_y, pin_z, pin_label, "
        "submitted_at, created_at, updated_at")
CHUNK = 200
for start in range(0, len(rows), CHUNK):
    print("WITH v(seq, building, title, descr, status, risk, dept, indoor, floor, room, location, space_type, "
          "space_code, space_name, unit_type, scene_id, px, py, pz, pin_label, at) AS (VALUES")
    lines = []
    for r in rows[start:start + CHUNK]:
        px, py, pz = r["pin"] or (None, None, None)
        lines.append("  (" + ", ".join([
            str(r["seq"]), q(r["building"]), q(r["title"]), q(r["desc"]), q(r["status"]), q(r["risk"]),
            q(r["dept"]), "TRUE" if r["indoor"] else "FALSE", q(r["floor"]), q(r["room"]), q(r["location"]),
            q(r["space_type"]), q(r["space_code"]), q(r["space_name"]), q(r["unit_type"]), q(r["scene_id"]),
            f"{q(px)}::float8", f"{q(py)}::float8", f"{q(pz)}::float8", q(r["pin_label"]),
            q(r["at"].isoformat()) + "::timestamptz",
        ]) + ")")
    print(",\n".join(lines))
    print(")")
    print(f"INSERT INTO reports ({cols})")
    print("SELECT 'DM-' || lpad(v.seq::text, 6, '0'), v.title, v.descr, v.status, v.risk, 'AI', d.id,")
    print("       (SELECT id FROM users WHERE provider = 'GOOGLE' AND provider_id = 'dummy-reporter'), 'REAL_NAME',")
    print("       b.id, b.name, v.indoor, v.floor, v.room, v.location, v.space_type, v.space_code, v.space_name,")
    print("       v.unit_type, v.scene_id, v.px, v.py, v.pz, v.pin_label, v.at, v.at, v.at")
    print("FROM v JOIN buildings b ON b.name = v.building JOIN departments d ON d.code = v.dept;")
print("COMMIT;")
