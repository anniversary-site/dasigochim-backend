package com.dasigochim.insight.dto;

import com.dasigochim.insight.domain.InsightPriority;
import com.dasigochim.insight.domain.ReportInsight;
import com.dasigochim.report.domain.Report;
import com.dasigochim.report.domain.RiskLevel;
import com.dasigochim.report.domain.RiskLevelSource;
import java.time.Instant;
import java.util.List;

/**
 * 관리자용 위험도 근거 + 인사이트. 제보자 대면 응답({@code ReportResponse})에는 넣지 않는다.
 *
 * <p>{@code insight} 는 아직 생성 전이거나 생성에 실패했으면 null 이다.
 */
public record ReportInsightResponse(Long reportId, String trackingId, RiskLevel riskLevel,
                                    RiskLevelSource riskLevelSource, String riskLevelRationale,
                                    Boolean riskLevelChanged, Insight insight) {

    public record Insight(String summary, List<String> keywords, boolean recurring, String insight,
                          InsightPriority recommendedPriority, List<String> relatedReportIds,
                          int similarCaseCount, Instant generatedAt) {}

    public static ReportInsightResponse of(Report report, ReportInsight insight) {
        return new ReportInsightResponse(report.getId(), report.getTrackingId(), report.getRiskLevel(),
                report.getRiskLevelSource(), report.getRiskLevelRationale(), report.getRiskLevelChanged(),
                insight == null ? null : new Insight(insight.getSummary(), insight.keywordList(),
                        insight.isRecurring(), insight.getInsight(), insight.getRecommendedPriority(),
                        insight.relatedReportIdList(), insight.getSimilarCaseCount(), insight.getUpdatedAt()));
    }
}
