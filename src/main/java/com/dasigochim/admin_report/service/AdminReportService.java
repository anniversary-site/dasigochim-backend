package com.dasigochim.admin_report.service;

import com.dasigochim.activitylog.domain.*;
import com.dasigochim.activitylog.repository.ReportActivityLogRepository;
import com.dasigochim.admin.repository.AdminRepository;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import com.dasigochim.common.response.PageResponse;
import com.dasigochim.department.repository.DepartmentRepository;
import com.dasigochim.file.hwp.HwpReportGenerator;
import com.dasigochim.notification.client.PushNotificationClient;
import com.dasigochim.notification.domain.Notification;
import com.dasigochim.notification.domain.NotificationType;
import com.dasigochim.notification.repository.NotificationRepository;
import com.dasigochim.notification.service.HighRiskNotificationService;
import com.dasigochim.report.domain.*;
import com.dasigochim.report.dto.ReportResponse;
import com.dasigochim.report.repository.ReportRepository;
import com.dasigochim.report.service.ReportViewService;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReportService {
    private final ReportRepository reportRepository;
    private final AdminRepository adminRepository;
    private final DepartmentRepository departmentRepository;
    private final ReportActivityLogRepository activityRepository;
    private final NotificationRepository notificationRepository;
    private final HighRiskNotificationService highRiskNotificationService;
    private final PushNotificationClient pushClient;
    private final ReportViewService viewService;
    private final HwpReportGenerator hwpReportGenerator;

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        Instant today = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new StatsResponse(reportRepository.countByStatus(ReportStatus.RECEIVED),
                reportRepository.countByStatus(ReportStatus.REVIEWING),
                reportRepository.countByStatus(ReportStatus.RESOLVED), reportRepository.countBySubmittedAtAfter(today));
    }

    @Transactional(readOnly = true)
    public AdminMeResponse me(Long adminId) {
        var admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new AdminMeResponse(admin.getName());
    }

    @Transactional(readOnly = true)
    public List<DepartmentPendingResponse> departments() {
        return departmentRepository.findAll().stream()
                .map(department -> new DepartmentPendingResponse(department.getId(), department.getName(),
                        reportRepository.countByDepartmentIdAndStatusNot(department.getId(), ReportStatus.RESOLVED)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> list(ReportStatus status, Long departmentId, String query, int page, int size, String sort) {
        Specification<Report> specification = (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.notEqual(root.get("status"), ReportStatus.RECEIVING));
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (departmentId != null) predicates.add(builder.equal(root.get("department").get("id"), departmentId));
            if (query != null && !query.isBlank()) {
                String like = "%" + query.toLowerCase() + "%";
                predicates.add(builder.or(builder.like(builder.lower(root.get("summary")), like),
                        builder.like(builder.lower(root.get("description")), like),
                        builder.like(builder.lower(root.get("trackingId")), like)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Sort ordering = "oldest".equalsIgnoreCase(sort) ? Sort.by("submittedAt").ascending() : Sort.by("submittedAt").descending();
        var result = reportRepository.findAll(specification, PageRequest.of(page, Math.min(size, 100), ordering))
                .map(report -> viewService.toResponse(report, false, false));
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public ReportResponse detail(Long id) {
        return viewService.toResponse(getReport(id), true, true);
    }

    @Transactional
    public ReportResponse changeStatus(Long id, Long adminId, ReportStatus status) {
        if (status != ReportStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "일반 상태 변경은 REVIEWING만 허용되며 완료는 resolve API를 사용해야 합니다.");
        }
        Report report = getReport(id);
        ReportStatus from = report.getStatus();
        report.transitionTo(status);
        activityRepository.save(ReportActivityLog.status(report, from, status, ActorType.ADMIN, adminId,
                null, ActivityType.STATUS_CHANGE));
        notificationRepository.save(new Notification(report, report.getReporter(), NotificationType.STATUS_CHANGED,
                "신고 상태가 변경되었습니다", report.getTrackingId() + " 신고가 " + status + " 상태로 변경되었습니다."));
        return viewService.toResponse(report, true, true);
    }

    @Transactional
    public ReportResponse reassign(Long id, Long adminId, Long departmentId) {
        Report report = getReport(id);
        var department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        Long fromId = report.getDepartment() == null ? null : report.getDepartment().getId();
        report.reassignDepartment(department);
        activityRepository.save(ReportActivityLog.department(report, fromId, departmentId, adminId));
        return viewService.toResponse(report, true, true);
    }

    @Transactional
    public ReportResponse adjustRisk(Long id, RiskLevel riskLevel) {
        Report report = getReport(id);
        RiskLevel previousRiskLevel = report.getRiskLevel();
        report.adjustRiskLevel(riskLevel);
        highRiskNotificationService.notifyRiskEscalated(report, previousRiskLevel);
        return viewService.toResponse(report, true, true);
    }

    @Transactional
    public ReportResponse resolve(Long id, Long adminId, String actionNote) {
        if (actionNote == null || actionNote.isBlank()) throw new BusinessException(ErrorCode.INVALID_REQUEST, "조치 내용은 필수입니다.");
        Report report = getReport(id);
        ReportStatus from = report.getStatus();
        report.transitionTo(ReportStatus.RESOLVED);
        activityRepository.save(ReportActivityLog.status(report, from, ReportStatus.RESOLVED,
                ActorType.ADMIN, adminId, actionNote, ActivityType.RESOLVE));
        Notification notification = notificationRepository.save(new Notification(report, report.getReporter(), NotificationType.RESOLVED,
                "신고 처리가 완료되었습니다", actionNote));
        if (pushClient.send(report.getReporter().getPushToken(), notification.getTitle(), notification.getMessage())) notification.markSent();
        return viewService.toResponse(report, true, true);
    }

    @Transactional(readOnly = true)
    public String reportFile(Long id) {
        String url = getReport(id).getReportFileUrl();
        if (url == null) throw new BusinessException(ErrorCode.REPORT_NOT_FOUND, "생성된 보고서 파일이 없습니다.");
        return url;
    }

    /** 관리자 대시보드에서 현재 신고 정보로 HWP를 다시 생성한다. */
    @Transactional
    public String generateReportFile(Long id) {
        Report report = getReport(id);
        String url = hwpReportGenerator.generate(report);
        report.attachReportFile(url);
        return url;
    }

    private Report getReport(Long id) {
        return reportRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }

    public record StatsResponse(long received, long reviewing, long resolved, long todayNew) {}
    public record AdminMeResponse(String name) {}
    public record DepartmentPendingResponse(Long id, String name, long pendingCount) {}
}
