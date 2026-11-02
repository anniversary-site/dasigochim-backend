package com.dasigochim.report.service;

import com.dasigochim.activitylog.domain.*;
import com.dasigochim.activitylog.repository.ReportActivityLogRepository;
import com.dasigochim.building.domain.Building;
import com.dasigochim.building.repository.BuildingRepository;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import com.dasigochim.file.hwp.HwpReportGenerator;
import com.dasigochim.notification.domain.Notification;
import com.dasigochim.notification.domain.NotificationType;
import com.dasigochim.notification.repository.NotificationRepository;
import com.dasigochim.notification.service.HighRiskNotificationService;
import com.dasigochim.report.domain.*;
import com.dasigochim.report.dto.ReportResponse;
import com.dasigochim.report.repository.ReportRepository;
import com.dasigochim.user.domain.User;
import com.dasigochim.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final BuildingRepository buildingRepository;
    private final ReportActivityLogRepository activityRepository;
    private final NotificationRepository notificationRepository;
    private final HighRiskNotificationService highRiskNotificationService;
    private final HwpReportGenerator hwpReportGenerator;
    private final ReportViewService viewService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReportResponse createDraft(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return viewService.toResponse(reportRepository.save(Report.draft(user)), false, false);
    }

    @Transactional
    public ReportResponse updateLocation(Long id, Long userId, LocationRequest request) {
        Report report = getOwnedDraft(id, userId);
        Building building = request.buildingId() == null ? null : buildingRepository.findById(request.buildingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUILDING_NOT_FOUND));
        report.updateLocation(building, request.locationDescription(), request.indoor(), request.floor(), request.room(),
                request.lat(), request.lng());
        return viewService.toResponse(report, false, false);
    }

    @Transactional
    public ReportResponse updateDraft(Long id, Long userId, UpdateRequest request) {
        Report report = getOwnedDraft(id, userId);
        report.updateDraft(request.summary(), request.description(), request.reporterType(), request.parentReportId());
        if (request.parentReportId() != null) report.linkParent(getReport(request.parentReportId()));
        return viewService.toResponse(report, false, false);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> duplicates(Long id, Long userId) {
        Report report = getOwned(id, userId);
        if (report.getBuilding() == null) return List.of();
        return reportRepository.findTop5ByBuildingIdAndIdNotAndStatusNotAndCreatedAtAfterOrderByCreatedAtDesc(
                        report.getBuilding().getId(), id, ReportStatus.RECEIVING, Instant.now().minusSeconds(30L * 86400))
                .stream().map(item -> viewService.toResponse(item, false, false)).toList();
    }

    @Transactional
    public ReportResponse generateReportFile(Long id, Long userId) {
        Report report = getOwned(id, userId);
        report.attachReportFile(hwpReportGenerator.generate(report));
        return viewService.toResponse(report, false, false);
    }

    @Transactional
    public ReportResponse submit(Long id, Long userId) {
        Report report = getOwnedDraft(id, userId);
        String trackingId = "SW-" + Instant.now().atZone(ZoneOffset.UTC).getYear() + "-" + String.format("%06d", report.getId());
        report.submit(trackingId);
        activityRepository.save(ReportActivityLog.status(report, ReportStatus.RECEIVING, ReportStatus.RECEIVED,
                ActorType.USER, userId, null, ActivityType.STATUS_CHANGE));
        notificationRepository.save(new Notification(report, report.getReporter(), NotificationType.SUBMITTED,
                "신고가 접수되었습니다", trackingId + " 신고가 정상적으로 접수되었습니다."));
        highRiskNotificationService.notifySubmittedHighRisk(report);
        // 담당자용 인사이트는 커밋 이후 비동기로 만든다. 제출 응답을 붙잡아 두지 않는다.
        eventPublisher.publishEvent(new ReportSubmittedEvent(report.getId()));
        return viewService.toResponse(report, true, false);
    }

    @Transactional
    public void deleteDraft(Long id, Long userId) {
        Report report = getOwnedDraft(id, userId);
        reportRepository.delete(report);
    }

    @Transactional(readOnly = true)
    public ReportResponse findByTrackingId(String trackingId) {
        Report report = reportRepository.findByTrackingId(trackingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        return viewService.toResponse(report, true, false);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> map(String filter, Long userId, String bbox) {
        List<Report> reports = "mine".equalsIgnoreCase(filter)
                ? reportRepository.findAllByReporterIdAndStatusNotOrderByCreatedAtDesc(requireUser(userId), ReportStatus.RECEIVING)
                : reportRepository.findAllByStatusNotOrderBySubmittedAtDesc(ReportStatus.RECEIVING);
        Bounds bounds = Bounds.parse(bbox);
        return reports.stream().filter(report -> bounds == null || bounds.contains(report.getLat(), report.getLng()))
                .map(report -> viewService.toResponse(report, false, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> byBuilding(Long buildingId) {
        if (!buildingRepository.existsById(buildingId)) throw new BusinessException(ErrorCode.BUILDING_NOT_FOUND);
        return reportRepository.findAllByBuildingIdAndStatusNotOrderBySubmittedAtDesc(buildingId, ReportStatus.RECEIVING)
                .stream().map(report -> viewService.toResponse(report, false, false)).toList();
    }

    public Report getOwnedDraft(Long id, Long userId) {
        Report report = getOwned(id, userId);
        if (report.getStatus() != ReportStatus.RECEIVING) throw new BusinessException(ErrorCode.REPORT_ALREADY_SUBMITTED);
        return report;
    }

    public Report getOwned(Long id, Long userId) {
        Report report = getReport(id);
        if (!report.getReporter().getId().equals(userId)) throw new BusinessException(ErrorCode.INVALID_REPORT_OWNER);
        return report;
    }

    public Report getReport(Long id) {
        return reportRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }

    private Long requireUser(Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.INVALID_TOKEN, "mine 필터는 로그인이 필요합니다.");
        return userId;
    }

    public record LocationRequest(Long buildingId, String locationDescription, Boolean indoor, String floor, String room,
                                  BigDecimal lat, BigDecimal lng) {}
    public record UpdateRequest(String summary, String description, ReporterType reporterType, Long parentReportId) {}

    private record Bounds(BigDecimal minLng, BigDecimal minLat, BigDecimal maxLng, BigDecimal maxLat) {
        static Bounds parse(String value) {
            if (value == null || value.isBlank()) return null;
            try {
                String[] values = value.split(",");
                if (values.length != 4) throw new IllegalArgumentException();
                return new Bounds(new BigDecimal(values[0]), new BigDecimal(values[1]),
                        new BigDecimal(values[2]), new BigDecimal(values[3]));
            } catch (RuntimeException exception) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "bbox는 minLng,minLat,maxLng,maxLat 형식이어야 합니다.");
            }
        }

        boolean contains(BigDecimal lat, BigDecimal lng) {
            if (lat == null || lng == null) return false;
            return lng.compareTo(minLng) >= 0 && lng.compareTo(maxLng) <= 0
                    && lat.compareTo(minLat) >= 0 && lat.compareTo(maxLat) <= 0;
        }
    }
}
