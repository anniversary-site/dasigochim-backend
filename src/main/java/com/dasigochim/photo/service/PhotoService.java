package com.dasigochim.photo.service;

import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import com.dasigochim.file.storage.FileStorageService;
import com.dasigochim.photo.domain.ReportPhoto;
import com.dasigochim.photo.repository.ReportPhotoRepository;
import com.dasigochim.report.dto.ReportResponse;
import com.dasigochim.report.service.ReportService;
import com.dasigochim.report.service.ReportViewService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PhotoService {
    private final ReportService reportService;
    private final ReportPhotoRepository photoRepository;
    private final FileStorageService storageService;
    private final ReportViewService viewService;
    @Value("${app.file.max-count}")
    private int maxCount;

    @Transactional
    public ReportResponse upload(Long reportId, Long userId, List<MultipartFile> files) {
        var report = reportService.getOwnedDraft(reportId, userId);
        long existing = photoRepository.countByReportId(reportId);
        if (files == null || files.isEmpty() || existing + files.size() > maxCount) {
            throw new BusinessException(ErrorCode.PHOTO_LIMIT_EXCEEDED);
        }
        int order = (int) existing;
        for (MultipartFile file : files) {
            if (file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미지 파일만 업로드할 수 있습니다.");
            }
            var stored = storageService.upload(file);
            photoRepository.save(new ReportPhoto(report, stored.url(), stored.originalFilename(),
                    stored.mimeType(), stored.sizeBytes(), order++));
        }
        return viewService.toResponse(report, false, false);
    }
}
