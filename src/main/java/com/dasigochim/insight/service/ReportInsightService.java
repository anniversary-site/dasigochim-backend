package com.dasigochim.insight.service;

import com.dasigochim.ai.service.AiAnalysisService;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import com.dasigochim.insight.domain.InsightPriority;
import com.dasigochim.insight.domain.ReportInsight;
import com.dasigochim.insight.dto.ReportInsightResponse;
import com.dasigochim.insight.repository.ReportInsightRepository;
import com.dasigochim.report.domain.Report;
import com.dasigochim.report.domain.ReportStatus;
import com.dasigochim.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ⑤ 요약·인사이트 생성/조회. 담당 부서 담당자용이며 제보자 대면 경로에서는 쓰지 않는다.
 *
 * <p>생성은 신고 제출 직후 비동기로 일어난다({@link ReportSubmittedInsightListener}).
 * 실패해도 신고 접수 자체는 이미 끝났으므로 로그만 남기고 넘어간다 — 관리자가 재생성할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class ReportInsightService {
    private static final Logger log = LoggerFactory.getLogger(ReportInsightService.class);

    private final ReportRepository reportRepository;
    private final ReportInsightRepository insightRepository;
    private final AiAnalysisService aiAnalysisService;

    /** 아직 생성 전이면 인사이트 없이 위험도 근거만 담아 돌려준다. */
    @Transactional(readOnly = true)
    public ReportInsightResponse find(Long reportId) {
        Report report = getSubmitted(reportId);
        return ReportInsightResponse.of(report, insightRepository.findByReportId(reportId).orElse(null));
    }

    /** 이미 있으면 덮어쓴다. 관리자가 재생성을 요청하거나 제출 직후 리스너가 호출한다. */
    @Transactional
    public ReportInsightResponse generate(Long reportId) {
        Report report = getSubmitted(reportId);
        var result = aiAnalysisService.generateInsight(report);

        ReportInsight insight = insightRepository.findByReportId(reportId).orElse(null);
        if (insight == null) {
            insight = insightRepository.save(new ReportInsight(report, result.summary(), result.keywords(),
                    result.recurring(), result.insight(), InsightPriority.from(result.recommendedPriority()),
                    result.relatedReportIds(), result.similarCaseCount()));
        } else {
            insight.update(result.summary(), result.keywords(), result.recurring(), result.insight(),
                    InsightPriority.from(result.recommendedPriority()), result.relatedReportIds(),
                    result.similarCaseCount());
        }
        log.info("신고 {} 인사이트 생성 완료 (priority={}, recurring={})",
                reportId, insight.getRecommendedPriority(), insight.isRecurring());
        return ReportInsightResponse.of(report, insight);
    }

    private Report getSubmitted(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        if (report.getStatus() == ReportStatus.RECEIVING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "제출된 신고에만 인사이트를 만들 수 있습니다.");
        }
        return report;
    }
}
