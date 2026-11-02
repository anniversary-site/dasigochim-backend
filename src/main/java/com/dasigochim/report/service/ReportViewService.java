package com.dasigochim.report.service;

import com.dasigochim.activitylog.repository.ReportActivityLogRepository;
import com.dasigochim.photo.repository.ReportPhotoRepository;
import com.dasigochim.report.domain.Report;
import com.dasigochim.report.dto.ReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReportViewService {
    private final ReportPhotoRepository photoRepository;
    private final ReportActivityLogRepository activityRepository;

    @Transactional(readOnly = true)
    public ReportResponse toResponse(Report report, boolean includeTimeline, boolean includeReporter) {
        var department = report.getDepartment() == null ? null
                : new ReportResponse.DepartmentInfo(report.getDepartment().getId(), report.getDepartment().getName());
        var building = report.getBuilding() == null ? null
                : new ReportResponse.BuildingInfo(report.getBuilding().getId(), report.getBuildingNameSnapshot(), report.getBuilding().getAddress());
        var photos = photoRepository.findAllByReportIdOrderByDisplayOrder(report.getId()).stream()
                .map(photo -> new ReportResponse.PhotoInfo(photo.getId(), photo.getUrl(), photo.getDisplayOrder())).toList();
        var timeline = includeTimeline ? activityRepository.findAllByReportIdOrderByCreatedAt(report.getId()).stream()
                .map(log -> new ReportResponse.ActivityInfo(log.getActivityType().name(), log.getFromStatus(), log.getToStatus(),
                        log.getActionNote(), log.getCreatedAt())).toList() : null;
        String reporterName = includeReporter && report.getReporterType() == com.dasigochim.report.domain.ReporterType.REAL_NAME
                ? report.getReporter().getName() : null;
        return new ReportResponse(report.getId(), report.getTrackingId(), report.getSummary(), report.getDescription(),
                report.getStatus(), report.getRiskLevel(), report.getRiskLevelSource(), department, report.getReporterType(),
                reporterName, building, report.getLocationDescription(), report.getIndoor(), report.getFloor(), report.getRoom(), report.getLat(), report.getLng(),
                report.getDetectedHazards(), report.getReportFileUrl(), photos, timeline, report.getSubmittedAt(), report.getCreatedAt());
    }
}
