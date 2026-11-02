package com.dasigochim.insight.repository;

import com.dasigochim.insight.domain.ReportInsight;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportInsightRepository extends JpaRepository<ReportInsight, Long> {
    Optional<ReportInsight> findByReportId(Long reportId);
}
