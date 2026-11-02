package com.dasigochim.internal.service;

import com.dasigochim.internal.dto.CaseSearchDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C-5 유사 과거사례 검색 + 동일 건물·기간 집계.
 *
 * <p>ai-server 의 {@code stub_client.search_cases} 가 <b>참조 구현</b>이다.
 * 정렬과 집계 규칙이 같아야 BACKEND_MODE 를 stub↔http 로 바꿔도 결과 해석이 갈리지 않는다.
 *
 * <p>1단계라 유사도는 쓰지 않는다. 목록은 "동일 건물 먼저, 그다음 최근순" 이고
 * {@code score} 는 null 이다. 2단계에서 ORDER BY 첫 줄만 벡터 거리로 바꾸면 되고
 * 응답 계약은 그대로다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseSearchService {
    private static final String CASES_SQL = """
            SELECT r.tracking_id                              AS report_id,
                   to_char(r.submitted_at, 'YYYY-MM-DD')      AS occurred_at,
                   r.status                                   AS status,
                   r.building_name_snapshot                   AS building_name,
                   r.floor                                    AS floor,
                   COALESCE(r.detected_hazards, '')           AS hazard,
                   COALESCE(l.action_note, '')                AS action_fundamental
            FROM reports r
            LEFT JOIN LATERAL (
                SELECT action_note
                FROM report_activity_logs
                WHERE report_id = r.id AND activity_type = 'RESOLVE'
                ORDER BY created_at DESC
                LIMIT 1
            ) l ON TRUE
            WHERE r.status <> 'RECEIVING'
              AND r.submitted_at IS NOT NULL
              AND (CAST(:report_id AS varchar) IS NULL OR r.tracking_id <> CAST(:report_id AS varchar))
            ORDER BY (CAST(:building_id AS bigint) IS NOT NULL
                      AND r.building_id IS DISTINCT FROM CAST(:building_id AS bigint)),
                     r.submitted_at DESC
            LIMIT :top_k
            """;

    private static final String STATS_SQL = """
            SELECT count(*)                                                   AS total,
                   count(*) FILTER (WHERE status IN ('RECEIVED','REVIEWING')) AS unresolved,
                   count(*) FILTER (WHERE floor = CAST(:floor AS varchar))    AS same_floor,
                   to_char(min(submitted_at), 'YYYY-MM-DD')                   AS first_occurred_at
            FROM reports
            WHERE building_id = CAST(:building_id AS bigint)
              AND status <> 'RECEIVING'
              AND submitted_at IS NOT NULL
              AND (CAST(:report_id AS varchar) IS NULL OR tracking_id <> CAST(:report_id AS varchar))
              AND submitted_at >= now() - make_interval(days => :window_days)
            """;

    private static final String RISK_SQL = """
            SELECT lower(risk_level) AS level, count(*) AS cnt
            FROM reports
            WHERE building_id = CAST(:building_id AS bigint)
              AND status <> 'RECEIVING'
              AND submitted_at IS NOT NULL
              AND risk_level IS NOT NULL
              AND (CAST(:report_id AS varchar) IS NULL OR tracking_id <> CAST(:report_id AS varchar))
              AND submitted_at >= now() - make_interval(days => :window_days)
            GROUP BY lower(risk_level)
            ORDER BY count(*) DESC
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public CaseSearchDto.Response search(CaseSearchDto.Request request) {
        List<CaseSearchDto.CaseItem> cases = findCases(request);
        // building_id 가 없으면 집계 대상이 없다. stats 를 null 로 두면 AI 서버가
        // "집계 안 함" 으로 읽고 is_recurring 을 false 로 둔다.
        CaseSearchDto.Stats stats = request.buildingId() == null ? null : aggregate(request);

        log.debug("C-5 cases/search session={} building={} window={}일 목록={}건 total={}",
                request.sessionId(), request.buildingId(), request.windowDaysOrDefault(),
                cases.size(), stats == null ? "-" : stats.total());
        return new CaseSearchDto.Response(cases, stats);
    }

    private List<CaseSearchDto.CaseItem> findCases(CaseSearchDto.Request request) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("report_id", blankToNull(request.reportId()))
                .addValue("building_id", request.buildingId())
                .addValue("top_k", request.topKOrDefault());

        // cases 는 window_days 에 묶이지 않는다. 오래된 건이라도 조치 내역 참고용으로 올라온다.
        return jdbc.query(CASES_SQL, params, (rs, rowNum) -> new CaseSearchDto.CaseItem(
                rs.getString("report_id"),
                rs.getString("occurred_at"),
                null, // 1단계는 항상 null. 2단계에서 벡터 거리로 채운다.
                rs.getString("status"),
                rs.getString("building_name"),
                rs.getString("floor"),
                rs.getString("hazard"),
                rs.getString("action_fundamental")));
    }

    private CaseSearchDto.Stats aggregate(CaseSearchDto.Request request) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("building_id", request.buildingId())
                .addValue("report_id", blankToNull(request.reportId()))
                .addValue("floor", blankToNull(request.floor()))
                .addValue("window_days", request.windowDaysOrDefault());

        return jdbc.queryForObject(STATS_SQL, params, (rs, rowNum) -> new CaseSearchDto.Stats(
                request.windowDaysOrDefault(),
                request.buildingName(),
                request.floor(),
                rs.getInt("total"),
                rs.getInt("unresolved"),
                rs.getInt("same_floor"),
                riskDistribution(params),
                rs.getString("first_occurred_at")));
    }

    /**
     * 위험 등급 분포. <b>키는 소문자</b>여야 한다 — AI 서버가 프롬프트에 그대로 인쇄하고,
     * ① 이 내는 등급도 소문자다. DB 에는 대문자로 저장돼 있어 SQL 에서 접는다.
     */
    private Map<String, Integer> riskDistribution(MapSqlParameterSource params) {
        Map<String, Integer> out = new LinkedHashMap<>();
        jdbc.query(RISK_SQL, params, rs -> {
            out.put(rs.getString("level"), rs.getInt("cnt"));
        });
        return out;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
