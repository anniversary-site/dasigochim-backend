package com.dasigochim.activitylog.domain;

import com.dasigochim.report.domain.Report;
import com.dasigochim.report.domain.ReportStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "report_activity_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportActivityLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "report_id", nullable = false)
    private Report report;
    @Enumerated(EnumType.STRING) @Column(name = "activity_type", nullable = false, length = 30)
    private ActivityType activityType;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 20)
    private ReportStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", length = 20)
    private ReportStatus toStatus;
    @Column(name = "from_department_id")
    private Long fromDepartmentId;
    @Column(name = "to_department_id")
    private Long toDepartmentId;
    @Enumerated(EnumType.STRING) @Column(name = "actor_type", nullable = false, length = 10)
    private ActorType actorType;
    @Column(name = "actor_id")
    private Long actorId;
    @Column(name = "action_note", columnDefinition = "TEXT")
    private String actionNote;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static ReportActivityLog status(Report report, ReportStatus from, ReportStatus to,
                                           ActorType actorType, Long actorId, String note, ActivityType type) {
        ReportActivityLog log = new ReportActivityLog();
        log.report = report; log.activityType = type; log.fromStatus = from; log.toStatus = to;
        log.actorType = actorType; log.actorId = actorId; log.actionNote = note; log.createdAt = Instant.now();
        return log;
    }

    public static ReportActivityLog department(Report report, Long fromId, Long toId, Long adminId) {
        ReportActivityLog log = new ReportActivityLog();
        log.report = report; log.activityType = ActivityType.DEPARTMENT_REASSIGN;
        log.fromDepartmentId = fromId; log.toDepartmentId = toId;
        log.actorType = ActorType.ADMIN; log.actorId = adminId; log.createdAt = Instant.now();
        return log;
    }
}
