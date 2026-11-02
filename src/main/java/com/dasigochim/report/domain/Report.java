package com.dasigochim.report.domain;

import com.dasigochim.building.domain.Building;
import com.dasigochim.common.entity.BaseTimeEntity;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import com.dasigochim.department.domain.Department;
import com.dasigochim.user.domain.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tracking_id", unique = true, length = 20)
    private String trackingId;
    @Column(length = 200)
    private String summary;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ReportStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "risk_level", length = 10)
    private RiskLevel riskLevel;
    @Enumerated(EnumType.STRING) @Column(name = "risk_level_source", nullable = false, length = 10)
    private RiskLevelSource riskLevelSource;
    @Column(name = "risk_level_rationale", columnDefinition = "TEXT")
    private String riskLevelRationale;
    @Column(name = "risk_level_changed")
    private Boolean riskLevelChanged;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "department_id")
    private Department department;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;
    @Enumerated(EnumType.STRING) @Column(name = "reporter_type", nullable = false, length = 15)
    private ReporterType reporterType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "building_id")
    private Building building;
    @Column(name = "building_name_snapshot", length = 100)
    private String buildingNameSnapshot;
    @Column(name = "location_description", length = 200)
    private String locationDescription;
    @Column(name = "is_indoor")
    private Boolean indoor;
    @Column(length = 10)
    private String floor;
    @Column(length = 30)
    private String room;
    @Column(precision = 10, scale = 7)
    private BigDecimal lat;
    @Column(precision = 10, scale = 7)
    private BigDecimal lng;
    @Enumerated(EnumType.STRING) @Column(name = "space_type", length = 10)
    private SpaceType spaceType;
    @Column(name = "space_code", length = 30)
    private String spaceCode;
    @Column(name = "space_name", length = 60)
    private String spaceName;
    @Column(length = 10)
    private String dong;
    @Column(name = "unit_type", length = 10)
    private String unitType;
    /** 위치를 찍은 3D 장면 id (예: unit_42-type, neighborhood_2F). 프론트의 /scenes/{id}_3d.html 과 짝이다. */
    @Column(name = "scene_id", length = 40)
    private String sceneId;
    /** 3D 장면 좌표계(미터)에서의 핀 위치. */
    @Column(name = "pin_x")
    private Double pinX;
    @Column(name = "pin_y")
    private Double pinY;
    @Column(name = "pin_z")
    private Double pinZ;
    /** 핀에서 가장 가까운 공간 이름 (예: 욕실, 피트니스). */
    @Column(name = "pin_label", length = 60)
    private String pinLabel;
    @Column(name = "detected_hazards", columnDefinition = "TEXT")
    private String detectedHazards;
    @Column(name = "report_file_url", length = 500)
    private String reportFileUrl;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_report_id")
    private Report parentReport;
    @Column(name = "submitted_at")
    private Instant submittedAt;

    public static Report draft(User reporter) {
        Report report = new Report();
        report.reporter = reporter;
        report.reporterType = ReporterType.ANONYMOUS;
        report.status = ReportStatus.RECEIVING;
        report.riskLevelSource = RiskLevelSource.AI;
        return report;
    }

    public void updateLocation(Building building, String locationDescription, Boolean indoor, String floor, String room,
                               BigDecimal lat, BigDecimal lng) {
        this.building = building;
        this.buildingNameSnapshot = building == null ? null : building.getName();
        this.locationDescription = locationDescription;
        this.indoor = indoor;
        this.floor = floor;
        this.room = room;
        this.lat = lat;
        this.lng = lng;
    }

    public void updateSpace(SpaceType spaceType, String spaceCode, String spaceName, String dong, String unitType,
                            String sceneId, Double pinX, Double pinY, Double pinZ, String pinLabel) {
        this.spaceType = spaceType;
        this.spaceCode = spaceCode;
        this.spaceName = spaceName;
        this.dong = dong;
        this.unitType = unitType;
        this.sceneId = sceneId;
        this.pinX = pinX;
        this.pinY = pinY;
        this.pinZ = pinZ;
        this.pinLabel = pinLabel;
    }

    public void applyAiAnalysis(String summary, String description, RiskLevel riskLevel,
                                Department department, String detectedHazards) {
        ensureDraft();
        this.summary = summary;
        this.description = description;
        this.riskLevel = riskLevel;
        this.riskLevelSource = RiskLevelSource.AI;
        this.department = department;
        this.detectedHazards = detectedHazards;
    }

    /** ④가 재산출한 위험 등급의 판단 근거. 등급 자체는 applyAiAnalysis 가 세운다. */
    public void applyRiskRationale(String rationale, Boolean changed) {
        this.riskLevelRationale = rationale;
        this.riskLevelChanged = changed;
    }

    public void updateDraft(String summary, String description, ReporterType reporterType, Long parentReportId) {
        ensureDraft();
        if (summary != null) this.summary = summary;
        if (description != null) this.description = description;
        if (reporterType != null) this.reporterType = reporterType;
    }

    public void linkParent(Report parentReport) {
        ensureDraft();
        this.parentReport = parentReport;
    }

    public void submit(String trackingId) {
        ensureDraft();
        this.trackingId = trackingId;
        this.status = ReportStatus.RECEIVED;
        this.submittedAt = Instant.now();
    }

    public void transitionTo(ReportStatus next) {
        boolean allowed = (status == ReportStatus.RECEIVED && next == ReportStatus.REVIEWING)
                || (status == ReportStatus.REVIEWING && next == ReportStatus.RESOLVED);
        if (!allowed) throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        this.status = next;
    }

    public void reassignDepartment(Department department) {
        this.department = department;
    }

    public void adjustRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
        this.riskLevelSource = RiskLevelSource.ADMIN;
        this.riskLevelRationale = null;
        this.riskLevelChanged = null;
    }

    public void attachReportFile(String url) {
        this.reportFileUrl = url;
    }

    private void ensureDraft() {
        if (status != ReportStatus.RECEIVING) throw new BusinessException(ErrorCode.REPORT_ALREADY_SUBMITTED);
    }
}
