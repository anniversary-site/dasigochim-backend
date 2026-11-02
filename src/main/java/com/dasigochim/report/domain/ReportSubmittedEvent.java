package com.dasigochim.report.domain;

/** 신고가 접수(제출)됐다. 커밋 이후 담당자용 인사이트 생성이 이 이벤트를 받는다. */
public record ReportSubmittedEvent(Long reportId) {}
