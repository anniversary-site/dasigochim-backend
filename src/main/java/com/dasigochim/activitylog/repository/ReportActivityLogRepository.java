package com.dasigochim.activitylog.repository;

import com.dasigochim.activitylog.domain.ReportActivityLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportActivityLogRepository extends JpaRepository<ReportActivityLog, Long> {
    List<ReportActivityLog> findAllByReportIdOrderByCreatedAt(Long reportId);
}
