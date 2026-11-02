package com.dasigochim.photo.repository;

import com.dasigochim.photo.domain.ReportPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportPhotoRepository extends JpaRepository<ReportPhoto, Long> {
    List<ReportPhoto> findAllByReportIdOrderByDisplayOrder(Long reportId);
    long countByReportId(Long reportId);
}
