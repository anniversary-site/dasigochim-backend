package com.dasigochim.notification.repository;

import com.dasigochim.notification.domain.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
    List<Notification> findAllByAdminIdOrderByCreatedAtDesc(Long adminId);
    Optional<Notification> findByIdAndAdminId(Long id, Long adminId);
}
