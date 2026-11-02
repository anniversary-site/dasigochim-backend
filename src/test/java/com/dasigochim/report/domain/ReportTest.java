package com.dasigochim.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.user.domain.User;
import org.junit.jupiter.api.Test;

class ReportTest {
    @Test
    void followsTheDefinedStatusTransition() {
        Report report = Report.draft(new User("GOOGLE", "google-1", "사용자"));

        report.submit("SW-2026-000001");
        report.transitionTo(ReportStatus.REVIEWING);
        report.transitionTo(ReportStatus.RESOLVED);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
    }

    @Test
    void rejectsAnInvalidStatusTransition() {
        Report report = Report.draft(new User("GOOGLE", "google-1", "사용자"));

        assertThatThrownBy(() -> report.transitionTo(ReportStatus.REVIEWING))
                .isInstanceOf(BusinessException.class);
    }
}
