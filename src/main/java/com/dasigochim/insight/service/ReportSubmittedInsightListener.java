package com.dasigochim.insight.service;

import com.dasigochim.report.domain.ReportSubmittedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 제출이 커밋된 뒤 담당자용 인사이트를 만든다.
 *
 * <p>AFTER_COMMIT 이라 비동기 스레드가 반드시 커밋된 신고를 읽는다.
 * 실패해도 접수는 이미 끝났으므로 삼키고 로그만 남긴다 — 관리자가 재생성할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class ReportSubmittedInsightListener {
    private static final Logger log = LoggerFactory.getLogger(ReportSubmittedInsightListener.class);

    private final ReportInsightService insightService;

    @Async("insightExecutor")
    @TransactionalEventListener
    public void onReportSubmitted(ReportSubmittedEvent event) {
        try {
            insightService.generate(event.reportId());
        } catch (RuntimeException exception) {
            log.warn("신고 {} 인사이트 생성 실패 — 접수는 완료됐다. 관리자 재생성으로 복구 가능: {}",
                    event.reportId(), exception.getMessage());
        }
    }
}
