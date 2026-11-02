package com.dasigochim.notification.service;

import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import com.dasigochim.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminNotificationService {
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> notifications(Long adminId) {
        return notificationRepository.findAllByAdminIdOrderByCreatedAtDesc(adminId).stream()
                .map(item -> new NotificationResponse(item.getId(), item.getReport().getTrackingId(), item.getType().name(),
                        item.getTitle(), item.getMessage(), item.isRead(), item.getCreatedAt())).toList();
    }

    @Transactional
    public void readNotification(Long adminId, Long notificationId) {
        var notification = notificationRepository.findByIdAndAdminId(notificationId, adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead();
    }

    public record NotificationResponse(Long id, String trackingId, String type, String title,
                                       String message, boolean read, Instant createdAt) {}
}
