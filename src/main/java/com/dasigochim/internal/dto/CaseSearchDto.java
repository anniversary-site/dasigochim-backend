package com.dasigochim.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;

/**
 * C-5 {@code POST /internal/v1/cases/search} 계약.
 *
 * <p>응답은 성격이 다른 두 덩어리다.
 * <ul>
 *   <li>{@code cases} — 관련 과거 신고 목록. 1단계는 "동일 건물 우선 → 최근순", {@code score} 는 null</li>
 *   <li>{@code stats} — 동일 건물·기간 집계. <b>순수 SQL</b> 이라 LLM 도 임베딩도 끼지 않는다</li>
 * </ul>
 */
public final class CaseSearchDto {
    private CaseSearchDto() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Request(
            String sessionId,
            String queryText,
            Integer topK,
            Integer windowDays,
            String reportId,
            Long buildingId,
            String buildingName,
            String floor) {

        public int topKOrDefault() {
            return topK == null || topK <= 0 ? 4 : topK;
        }

        /** 기간은 AI 서버가 정해서 보낸다(.env CASE_WINDOW_DAYS). 백엔드는 자체 설정을 두지 않는다. */
        public int windowDaysOrDefault() {
            return windowDays == null || windowDays <= 0 ? 30 : windowDays;
        }
    }

    /**
     * {@code score} 는 1단계에서 항상 null 이다 (2단계에서 벡터 거리로 채워진다).
     * {@code actionFundamental} 은 미해결 건이면 빈 문자열이고, AI 서버가
     * "(미조치 — 처리 진행 중)" 으로 표시한다.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CaseItem(
            String reportId,
            String occurredAt,
            Double score,
            String status,
            String buildingName,
            String floor,
            String hazard,
            String actionFundamental) {}

    /**
     * {@code total} 은 <b>이번 신고를 제외한</b> 기간 내 동일 건물 신고 건수다.
     * {@code sameFloor} 는 total 에 <b>포함된</b> 수치이지 합산 대상이 아니다.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Stats(
            int windowDays,
            String buildingName,
            String floor,
            int total,
            int unresolved,
            int sameFloor,
            Map<String, Integer> riskDistribution,
            String firstOccurredAt) {}

    /** {@code stats} 는 building_id 가 없으면 null 이다 — AI 서버가 "집계 안 함" 으로 읽는다. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Response(List<CaseItem> cases, Stats stats) {}
}
