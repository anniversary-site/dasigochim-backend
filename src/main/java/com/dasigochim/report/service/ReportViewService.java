package com.dasigochim.report.service;

import com.dasigochim.activitylog.repository.ReportActivityLogRepository;
import com.dasigochim.photo.repository.ReportPhotoRepository;
import com.dasigochim.report.domain.Report;
import com.dasigochim.report.domain.SpaceType;
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
        return toResponse(report, includeTimeline, includeReporter, false);
    }

    /**
     * 다른 입주민에게 보여줄 응답. 세대 내부 신고는 동·호수·층과 자유 서술 위치를 가리고
     * "세대 내부 · 42 TYPE · 욕실" 정도만 남긴다. 공용공간 신고는 그대로 보여준다.
     */
    @Transactional(readOnly = true)
    public ReportResponse toPublicResponse(Report report, boolean includeTimeline) {
        return toResponse(report, includeTimeline, false, report.getSpaceType() == SpaceType.UNIT);
    }

    private ReportResponse toResponse(Report report, boolean includeTimeline, boolean includeReporter, boolean mask) {
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
        var pin = report.getPinX() == null || report.getPinY() == null || report.getPinZ() == null ? null
                : new ReportResponse.Pin(report.getPinX(), report.getPinY(), report.getPinZ(), report.getPinLabel());
        var space = report.getSpaceType() == null && report.getSceneId() == null ? null
                : new ReportResponse.SpaceInfo(report.getSpaceType(), report.getSpaceCode(), report.getSpaceName(),
                        mask ? null : report.getDong(), report.getUnitType(), report.getSceneId(), pin, mask);
        String locationDescription = mask ? maskedLocation(report) : report.getLocationDescription();
        return new ReportResponse(report.getId(), report.getTrackingId(), report.getSummary(), report.getDescription(),
                report.getStatus(), report.getRiskLevel(), report.getRiskLevelSource(), department, report.getReporterType(),
                reporterName, building, locationDescription, report.getIndoor(),
                mask ? null : report.getFloor(), mask ? null : report.getRoom(), report.getLat(), report.getLng(),
                space, report.getDetectedHazards(), report.getReportFileUrl(), photos, timeline,
                report.getSubmittedAt(), report.getCreatedAt());
    }

    private static String maskedLocation(Report report) {
        StringBuilder text = new StringBuilder("세대 내부");
        if (report.getUnitType() != null) text.append(" · ").append(report.getUnitType()).append(" TYPE");
        if (report.getPinLabel() != null && !report.getPinLabel().isBlank()) text.append(" · ").append(report.getPinLabel());
        return text.toString();
    }
}
