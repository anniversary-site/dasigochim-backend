package com.dasigochim.notification.service;

import com.dasigochim.admin.repository.AdminRepository;
import com.dasigochim.admin.service.AdminAccessService;
import com.dasigochim.notification.domain.Notification;
import com.dasigochim.notification.domain.NotificationType;
import com.dasigochim.notification.repository.NotificationRepository;
import com.dasigochim.report.domain.Report;
import com.dasigochim.report.domain.RiskLevel;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HighRiskNotificationService {
    private final NotificationRepository notificationRepository;
    private final AdminRepository adminRepository;
    private final AdminAccessService accessService;

    public void notifySubmittedHighRisk(Report report) {
        if (report.getRiskLevel() != RiskLevel.HIGH) return;
        saveNotifications(report, "고위험 신고가 접수되었습니다");
    }

    public void notifyRiskEscalated(Report report, RiskLevel previousRiskLevel) {
        if (previousRiskLevel == RiskLevel.HIGH || report.getRiskLevel() != RiskLevel.HIGH) return;
        saveNotifications(report, "신고가 고위험으로 분류되었습니다");
    }

    private void saveNotifications(Report report, String userTitle) {
        String trackingId = report.getTrackingId() == null ? "작성 중 신고" : report.getTrackingId();
        List<Notification> notifications = new ArrayList<>();
        notifications.add(new Notification(report, report.getReporter(), NotificationType.HIGH_RISK,
                userTitle, trackingId + " 신고가 고위험으로 분류되어 신속히 검토됩니다."));
        // 담당 구역에 이 신고 단지가 들어가는 관리자에게만 알린다.
        adminRepository.findAll().stream().filter(admin -> accessService.covers(admin, report.getBuilding()))
                .forEach(admin -> notifications.add(new Notification(report, admin, NotificationType.HIGH_RISK,
                "고위험 신고 알림", trackingId + " 고위험 신고가 접수되었습니다. 확인이 필요합니다.")));
        notificationRepository.saveAll(notifications);
    }
}
