package com.dasigochim.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.dasigochim.report.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportResponse(
        Long id, String trackingId, String summary, String description, ReportStatus status,
        RiskLevel riskLevel, RiskLevelSource riskLevelSource, DepartmentInfo department,
        ReporterType reporterType, String reporterName, BuildingInfo building,
        String locationDescription, Boolean indoor, String floor, String room, BigDecimal lat, BigDecimal lng,
        String detectedHazards, String reportFileUrl, List<PhotoInfo> photos,
        List<ActivityInfo> timeline, Instant submittedAt, Instant createdAt) {
    public record DepartmentInfo(Long id, String name) {}
    public record BuildingInfo(Long id, String name, String address) {}
    public record PhotoInfo(Long id, String url, int displayOrder) {}
    public record ActivityInfo(String type, ReportStatus fromStatus, ReportStatus toStatus,
                               String actionNote, Instant createdAt) {}
}
