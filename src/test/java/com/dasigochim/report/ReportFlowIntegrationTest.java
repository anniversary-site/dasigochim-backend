package com.dasigochim.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.dasigochim.activitylog.repository.ReportActivityLogRepository;
import com.dasigochim.admin.domain.Admin;
import com.dasigochim.admin.repository.AdminRepository;
import com.dasigochim.admin_report.service.AdminReportService;
import com.dasigochim.department.domain.Department;
import com.dasigochim.department.repository.DepartmentRepository;
import com.dasigochim.notification.repository.NotificationRepository;
import com.dasigochim.report.domain.ReportStatus;
import com.dasigochim.report.domain.RiskLevel;
import com.dasigochim.report.repository.ReportRepository;
import com.dasigochim.report.service.ReportService;
import com.dasigochim.user.domain.User;
import com.dasigochim.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportFlowIntegrationTest {
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired ReportService reportService;
    @Autowired AdminReportService adminReportService;
    @Autowired AdminRepository adminRepository;
    @Autowired ReportActivityLogRepository activityRepository;
    @Autowired NotificationRepository notificationRepository;

    @Test
    void draftSubmitReviewAndResolveProducesHistoryAndNotifications() {
        User user = userRepository.save(new User("GOOGLE", "integration-google", "통합테스트 사용자"));
        Department department = departmentRepository.save(new Department("통합테스트 시설팀", "TEST_FACILITY"));

        Long reportId = reportService.createDraft(user.getId()).id();
        reportRepository.findById(reportId).orElseThrow().reassignDepartment(department);

        reportService.submit(reportId, user.getId());
        Long adminId = adminRepository.save(new Admin("flow-admin", "hash", "흐름 관리자", department)).getId();
        adminReportService.changeStatus(reportId, adminId, ReportStatus.REVIEWING);
        adminReportService.resolve(reportId, adminId, "현장 보수를 완료했습니다.");

        assertThat(reportRepository.findById(reportId).orElseThrow().getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(activityRepository.findAllByReportIdOrderByCreatedAt(reportId)).hasSize(3);
        assertThat(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())).hasSize(3);
    }

    @Test
    void highRiskSubmitNotifiesReporterAndEveryAdmin() {
        User user = userRepository.save(new User("GOOGLE", "high-risk-google", "고위험 신고자"));
        Department department = departmentRepository.save(new Department("고위험 시설팀", "HIGH_RISK_FACILITY"));
        Admin firstAdmin = adminRepository.save(new Admin("high-risk-admin-1", "hash", "관리자1", department));
        Admin secondAdmin = adminRepository.save(new Admin("high-risk-admin-2", "hash", "관리자2", department));

        Long reportId = reportService.createDraft(user.getId()).id();
        reportRepository.findById(reportId).orElseThrow().applyAiAnalysis(
                "통행 불가 위험", "시설물 붕괴 위험", RiskLevel.HIGH, department, "붕괴 위험");

        reportService.submit(reportId, user.getId());

        assertThat(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()))
                .extracting(notification -> notification.getType().name())
                .contains("SUBMITTED", "HIGH_RISK");
        assertThat(notificationRepository.findAllByAdminIdOrderByCreatedAtDesc(firstAdmin.getId()))
                .extracting(notification -> notification.getType().name())
                .containsExactly("HIGH_RISK");
        assertThat(notificationRepository.findAllByAdminIdOrderByCreatedAtDesc(secondAdmin.getId()))
                .extracting(notification -> notification.getType().name())
                .containsExactly("HIGH_RISK");
    }
}
