package com.dasigochim.user.service;

import com.dasigochim.building.service.BuildingService;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import com.dasigochim.notification.repository.NotificationRepository;
import com.dasigochim.report.domain.ReportStatus;
import com.dasigochim.report.dto.ReportResponse;
import com.dasigochim.report.repository.ReportRepository;
import com.dasigochim.report.service.ReportViewService;
import com.dasigochim.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeService {
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final NotificationRepository notificationRepository;
    private final ReportViewService viewService;
    private final BuildingService buildingService;

    @Transactional(readOnly = true)
    public ProfileResponse profile(Long userId) {
        var user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return ProfileResponse.from(user);
    }

    /** 이름·연락처와 거주 단지·동·호수를 저장한다. 호수는 단지의 세대 표에 있어야 한다. */
    @Transactional
    public ProfileResponse updateProfile(Long userId, String name, String phone, Long buildingId, String dong, String ho) {
        var user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        var building = buildingService.getEntity(buildingId);
        var unit = buildingService.findUnit(buildingId, dong, ho);
        user.updateProfile(name.trim(), user.getMajor(), user.getStudentNo(), phone.trim());
        user.updateResidence(building, unit.getDong(), unit.getHo());
        return ProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> reports(Long userId) {
        return reportRepository.findAllByReporterIdAndStatusNotOrderByCreatedAtDesc(userId, ReportStatus.RECEIVING)
                .stream().map(report -> viewService.toResponse(report, true, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> notifications(Long userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(item -> new NotificationResponse(item.getId(), item.getReport().getTrackingId(), item.getType().name(),
                        item.getTitle(), item.getMessage(), item.isRead(), item.getCreatedAt())).toList();
    }

    @Transactional
    public void readNotification(Long userId, Long notificationId) {
        var notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead();
    }

    public record ProfileResponse(Long id, String name, String phone, BuildingInfo building, String dong, String ho,
                                  boolean profileCompleted) {
        static ProfileResponse from(com.dasigochim.user.domain.User user) {
            var building = user.getBuilding() == null ? null
                    : new BuildingInfo(user.getBuilding().getId(), user.getBuilding().getName());
            return new ProfileResponse(user.getId(), user.getName(), user.getPhone(), building, user.getDong(),
                    user.getHo(), user.isProfileCompleted());
        }
    }
    public record BuildingInfo(Long id, String name) {}
    public record NotificationResponse(Long id, String trackingId, String type, String title,
                                       String message, boolean read, Instant createdAt) {}
}
