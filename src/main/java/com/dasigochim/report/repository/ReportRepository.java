package com.dasigochim.report.repository;

import com.dasigochim.report.domain.Report;
import com.dasigochim.report.domain.ReportStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {
    Optional<Report> findByTrackingId(String trackingId);
    List<Report> findAllByStatusNotOrderBySubmittedAtDesc(ReportStatus status);
    List<Report> findAllByReporterIdAndStatusNotOrderByCreatedAtDesc(Long reporterId, ReportStatus status);
    List<Report> findAllByBuildingIdAndStatusNotOrderBySubmittedAtDesc(Long buildingId, ReportStatus status);
    List<Report> findTop5ByBuildingIdAndIdNotAndStatusNotAndCreatedAtAfterOrderByCreatedAtDesc(
            Long buildingId, Long id, ReportStatus status, Instant createdAfter);
    long countByStatus(ReportStatus status);
    long countBySubmittedAtAfter(Instant instant);
    long countByDepartmentIdAndStatusNot(Long departmentId, ReportStatus status);
}
