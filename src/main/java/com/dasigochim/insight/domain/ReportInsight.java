package com.dasigochim.insight.domain;

import com.dasigochim.common.entity.BaseTimeEntity;
import com.dasigochim.report.domain.Report;
import jakarta.persistence.*;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ⑤ 요약·인사이트 결과. 담당 부서 담당자용 내부 정보이며 제보자에게 노출하지 않는다.
 *
 * <p>신고 제출 후 비동기로 만들어지므로 신고 1건에 아직 없을 수 있다.
 * 목록 형태의 두 값(keywords, relatedReportIds)은 조회만 하므로 콤마로 이어 한 칸에 담는다.
 */
@Getter
@Entity
@Table(name = "report_insights")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportInsight extends BaseTimeEntity {
    private static final String SEPARATOR = ",";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "report_id", nullable = false, unique = true)
    private Report report;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;
    @Column(columnDefinition = "TEXT")
    private String keywords;
    @Column(nullable = false)
    private boolean recurring;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String insight;
    @Enumerated(EnumType.STRING) @Column(name = "recommended_priority", nullable = false, length = 10)
    private InsightPriority recommendedPriority;
    @Column(name = "related_report_ids", columnDefinition = "TEXT")
    private String relatedReportIds;
    @Column(name = "similar_case_count", nullable = false)
    private int similarCaseCount;

    public ReportInsight(Report report, String summary, List<String> keywords, boolean recurring, String insight,
                         InsightPriority recommendedPriority, List<String> relatedReportIds, int similarCaseCount) {
        this.report = report;
        update(summary, keywords, recurring, insight, recommendedPriority, relatedReportIds, similarCaseCount);
    }

    /** 재생성 시 같은 행을 덮어쓴다 (신고 1건에 인사이트 1개). */
    public void update(String summary, List<String> keywords, boolean recurring, String insight,
                       InsightPriority recommendedPriority, List<String> relatedReportIds, int similarCaseCount) {
        this.summary = summary;
        this.keywords = join(keywords);
        this.recurring = recurring;
        this.insight = insight;
        this.recommendedPriority = recommendedPriority;
        this.relatedReportIds = join(relatedReportIds);
        this.similarCaseCount = similarCaseCount;
    }

    public List<String> keywordList() {
        return split(keywords);
    }

    public List<String> relatedReportIdList() {
        return split(relatedReportIds);
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(SEPARATOR, values);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split(SEPARATOR)).stream().map(String::trim).filter(item -> !item.isEmpty()).toList();
    }
}
