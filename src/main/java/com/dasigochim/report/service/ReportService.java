package com.dasigochim.report.service;

import com.dasigochim.activitylog.domain.*;
import com.dasigochim.activitylog.repository.ReportActivityLogRepository;
import com.dasigochim.building.domain.Building;
import com.dasigochim.building.repository.BuildingRepository;
import com.dasigochim.building.service.BuildingService;
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
    private final BuildingService buildingService;
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

    /**
     * 신고 위치를 저장한다. 세대 내부는 동·호수로 세대 표를 찾아 타입과 3D 장면을 서버가 정하고,
     * 공용공간은 공간 코드로 장면을 정한다. 핀 좌표는 그 장면 좌표계 그대로 받는다.
     */
    @Transactional
    public ReportResponse updateLocation(Long id, Long userId, LocationRequest request) {
        Report report = getOwnedDraft(id, userId);
        Building building = request.buildingId() == null ? null : buildingRepository.findById(request.buildingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUILDING_NOT_FOUND));
        String floor = request.floor();
        String room = request.room();
        String spaceName = request.spaceName();
        String unitType = null;
        String sceneId = request.sceneId();
        String dong = request.dong();
        if (building != null && request.spaceType() == SpaceType.UNIT && !buildingService.hasUnits(building.getId())) {
            // 3D 평면이 없는 단지는 세대 표도 없다. 사용자가 적은 동·호수를 그대로 둔다.
            room = BuildingService.normalize(request.room());
            dong = BuildingService.normalize(request.dong());
            sceneId = null;
            if (spaceName == null || spaceName.isBlank()) spaceName = "세대 내부";
        } else if (building != null && request.spaceType() == SpaceType.UNIT) {
            var unit = buildingService.findUnit(building.getId(), request.dong(), request.room());
            floor = unit.getFloor();
            room = unit.getHo();
            dong = unit.getDong();
            unitType = unit.getUnitType();
            sceneId = unit.getSceneId();
            if (spaceName == null || spaceName.isBlank()) spaceName = "세대 내부";
        } else if (building != null && request.spaceType() == SpaceType.COMMON && request.spaceCode() != null) {
            var space = buildingService.findSpace(building.getId(), request.spaceCode());
            sceneId = space.getSceneId();
            spaceName = space.getName();
            if (!space.isRequiresFloor()) floor = space.getFloorLabel();
            room = null;
            dong = null;
        }
        report.updateLocation(building, request.locationDescription(), request.indoor() == null ? Boolean.TRUE : request.indoor(),
                floor, room, request.lat(), request.lng());
        report.updateSpace(request.spaceType(), request.spaceCode(), spaceName, dong, unitType, sceneId,
                request.pinX(), request.pinY(), request.pinZ(), request.pinLabel());
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
        String trackingId = "DG-" + Instant.now().atZone(ZoneOffset.UTC).getYear() + "-" + String.format("%06d", report.getId());
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

    /**
     * 신고 상세. 본인 신고는 전부 보여주고, 같은 단지 입주민에게는 세대 내부 위치를 가려 보여준다.
     * 그 밖의 사람에게는 보여주지 않는다.
     */
    @Transactional(readOnly = true)
    public ReportResponse findByTrackingId(String trackingId, Long viewerId) {
        Report report = reportRepository.findByTrackingId(trackingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        if (viewerId != null && report.getReporter().getId().equals(viewerId)) {
            return viewService.toResponse(report, true, false);
        }
        User viewer = viewerId == null ? null : userRepository.findById(viewerId).orElse(null);
        if (viewer == null || viewer.getBuilding() == null || report.getBuilding() == null
                || !viewer.getBuilding().getId().equals(report.getBuilding().getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "같은 청년주택 입주민의 신고만 볼 수 있습니다.");
        }
        return viewService.toPublicResponse(report, true);
    }

    /**
     * 메인 화면 목록. mine = 내 신고, others = 같은 단지의 다른 입주민 신고(세대 내부 위치는 가림).
     * 거주 단지를 아직 정하지 않은 사용자의 others 는 빈 목록이다.
     */
    @Transactional(readOnly = true)
    public List<ReportResponse> feed(String filter, Long userId) {
        User user = userRepository.findById(requireUser(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if ("mine".equalsIgnoreCase(filter)) {
            return reportRepository.findAllByReporterIdAndStatusNotOrderByCreatedAtDesc(user.getId(), ReportStatus.RECEIVING)
                    .stream().map(report -> viewService.toResponse(report, false, false)).toList();
        }
        if (user.getBuilding() == null) return List.of();
        return reportRepository.findAllByBuildingIdAndStatusNotOrderBySubmittedAtDesc(user.getBuilding().getId(), ReportStatus.RECEIVING)
                .stream()
                .filter(report -> !report.getReporter().getId().equals(user.getId()))
                .map(report -> viewService.toPublicResponse(report, false)).toList();
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
        if (userId == null) throw new BusinessException(ErrorCode.INVALID_TOKEN, "로그인이 필요합니다.");
        return userId;
    }

    /** room 은 호수다. spaceType 이 UNIT 이면 dong·room 으로 세대를 찾는다. */
    public record LocationRequest(Long buildingId, String locationDescription, Boolean indoor, String floor, String room,
                                  BigDecimal lat, BigDecimal lng, SpaceType spaceType, String spaceCode,
                                  String spaceName, String dong, String sceneId, Double pinX, Double pinY, Double pinZ,
                                  String pinLabel) {}
    public record UpdateRequest(String summary, String description, ReporterType reporterType, Long parentReportId) {}
}
