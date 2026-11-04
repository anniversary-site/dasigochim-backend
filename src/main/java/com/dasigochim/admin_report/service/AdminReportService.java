package com.dasigochim.admin_report.service;

import com.dasigochim.activitylog.domain.*;
import com.dasigochim.activitylog.repository.ReportActivityLogRepository;
import com.dasigochim.admin.service.AdminAccessService;
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
    private final AdminAccessService accessService;
    private final DepartmentRepository departmentRepository;
    private final ReportActivityLogRepository activityRepository;
    private final NotificationRepository notificationRepository;
    private final HighRiskNotificationService highRiskNotificationService;
    private final PushNotificationClient pushClient;
    private final ReportViewService viewService;
    private final HwpReportGenerator hwpReportGenerator;

    /** 담당 범위 안 신고만 고르는 조건. SUPER 면 조건 없음, 맡은 단지가 없으면 아무것도 안 나온다. */
    private Specification<Report> inScope(AdminAccessService.Access access) {
        return (root, criteria, builder) -> {
            if (access.all()) return builder.conjunction();
            if (access.buildingIds().isEmpty()) return builder.disjunction();
            return root.get("building").get("id").in(access.buildingIds());
        };
    }

    private static Specification<Report> withStatus(ReportStatus status) {
        return (root, criteria, builder) -> builder.equal(root.get("status"), status);
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(Long adminId) {
        var scope = inScope(accessService.access(adminId));
        Instant today = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Specification<Report> todayNew = (root, criteria, builder) -> builder.greaterThan(root.get("submittedAt"), today);
        return new StatsResponse(reportRepository.count(scope.and(withStatus(ReportStatus.RECEIVED))),
                reportRepository.count(scope.and(withStatus(ReportStatus.REVIEWING))),
                reportRepository.count(scope.and(withStatus(ReportStatus.RESOLVED))),
                reportRepository.count(scope.and(todayNew)));
    }

    @Transactional(readOnly = true)
    public AdminMeResponse me(Long adminId) {
        var access = accessService.access(adminId);
        var admin = access.admin();
        var scopes = access.scopes().stream()
                .map(scope -> new ScopeResponse(scope.getSido(), scope.getSigungu(),
                        scope.getBuilding() == null ? null : scope.getBuilding().getId(),
                        scope.getBuilding() == null ? null : scope.getBuilding().getName(),
                        scope.getBuilding() == null ? null : scope.getBuilding().getSido(),
                        scope.getBuilding() == null ? null : scope.getBuilding().getSigungu()))
                .toList();
        return new AdminMeResponse(admin.getName(), admin.getRole().name(), admin.getRole().label(),
                access.scopeLabel(), admin.getDepartment().getName(), scopes);
    }

    @Transactional(readOnly = true)
    public List<DepartmentPendingResponse> departments(Long adminId) {
        var scope = inScope(accessService.access(adminId));
        return departmentRepository.findAll().stream()
                .map(department -> new DepartmentPendingResponse(department.getId(), department.getName(),
                        reportRepository.count(scope.and((root, criteria, builder) -> builder.and(
                                builder.equal(root.get("department").get("id"), department.getId()),
                                builder.notEqual(root.get("status"), ReportStatus.RESOLVED))))))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> list(Long adminId, ReportStatus status, Long departmentId, String query, int page, int size, String sort) {
        Specification<Report> filters = (root, criteria, builder) -> {
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
        Specification<Report> specification = inScope(accessService.access(adminId)).and(filters);
        Sort ordering = "oldest".equalsIgnoreCase(sort) ? Sort.by("submittedAt").ascending() : Sort.by("submittedAt").descending();
        var result = reportRepository.findAll(specification, PageRequest.of(page, Math.min(size, 100), ordering))
                .map(report -> viewService.toResponse(report, false, false));
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public ReportResponse detail(Long id, Long adminId) {
        return viewService.toResponse(getReport(id, adminId), true, true);
    }

    @Transactional
    public ReportResponse changeStatus(Long id, Long adminId, ReportStatus status) {
        if (status != ReportStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "일반 상태 변경은 REVIEWING만 허용되며 완료는 resolve API를 사용해야 합니다.");
        }
        Report report = getReport(id, adminId);
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
        Report report = getReport(id, adminId);
        var department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        Long fromId = report.getDepartment() == null ? null : report.getDepartment().getId();
        report.reassignDepartment(department);
        activityRepository.save(ReportActivityLog.department(report, fromId, departmentId, adminId));
        return viewService.toResponse(report, true, true);
    }

    @Transactional
    public ReportResponse adjustRisk(Long id, Long adminId, RiskLevel riskLevel) {
        Report report = getReport(id, adminId);
        RiskLevel previousRiskLevel = report.getRiskLevel();
        report.adjustRiskLevel(riskLevel);
        highRiskNotificationService.notifyRiskEscalated(report, previousRiskLevel);
        return viewService.toResponse(report, true, true);
    }

    @Transactional
    public ReportResponse resolve(Long id, Long adminId, String actionNote) {
        if (actionNote == null || actionNote.isBlank()) throw new BusinessException(ErrorCode.INVALID_REQUEST, "조치 내용은 필수입니다.");
        Report report = getReport(id, adminId);
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
    public String reportFile(Long id, Long adminId) {
        String url = getReport(id, adminId).getReportFileUrl();
        if (url == null) throw new BusinessException(ErrorCode.REPORT_NOT_FOUND, "생성된 보고서 파일이 없습니다.");
        return url;
    }

    /** 관리자 대시보드에서 현재 신고 정보로 HWP를 다시 생성한다. */
    @Transactional
    public String generateReportFile(Long id, Long adminId) {
        Report report = getReport(id, adminId);
        String url = hwpReportGenerator.generate(report);
        report.attachReportFile(url);
        return url;
    }

    /** 신고를 찾고, 담당 범위 밖이면 403 */
    private Report getReport(Long id, Long adminId) {
        Report report = reportRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        accessService.assertReport(adminId, report);
        return report;
    }

    /** 다른 관리자 API(인사이트 등)가 범위 확인만 할 때 */
    @Transactional(readOnly = true)
    public void assertAccess(Long id, Long adminId) {
        getReport(id, adminId);
    }

    public record StatsResponse(long received, long reviewing, long resolved, long todayNew) {}
    public record ScopeResponse(String sido, String sigungu, Long buildingId, String buildingName,
                                String buildingSido, String buildingSigungu) {}
    /** role: SUPER / REGION / BUILDING, scopeLabel: "전국" / "서울특별시 마포구" / 단지 이름 */
    public record AdminMeResponse(String name, String role, String roleLabel, String scopeLabel,
                                  String departmentName, List<ScopeResponse> scopes) {}
    public record DepartmentPendingResponse(Long id, String name, long pendingCount) {}
}
