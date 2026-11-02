package com.dasigochim.notification.domain;

import com.dasigochim.admin.domain.Admin;
import com.dasigochim.report.domain.Report;
import com.dasigochim.user.domain.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "report_id", nullable = false)
    private Report report;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "admin_id")
    private Admin admin;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private NotificationType type;
    @Column(nullable = false, length = 100)
    private String title;
    @Column(nullable = false, length = 300)
    private String message;
    @Column(name = "is_read", nullable = false)
    private boolean read;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Notification(Report report, User user, NotificationType type, String title, String message) {
        this.report = report; this.user = user; this.type = type; this.title = title; this.message = message;
        this.createdAt = Instant.now();
    }

    public Notification(Report report, Admin admin, NotificationType type, String title, String message) {
        this.report = report; this.admin = admin; this.type = type; this.title = title; this.message = message;
        this.createdAt = Instant.now();
    }

    public void markRead() { this.read = true; }
    public void markSent() { this.sentAt = Instant.now(); }
}
