package com.dasigochim.ai.service;

import com.dasigochim.ai.client.AiAnalysisClient;
import com.dasigochim.building.repository.BuildingRepository;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import com.dasigochim.department.repository.DepartmentRepository;
import com.dasigochim.file.storage.FileStorageService;
import com.dasigochim.photo.domain.ReportPhoto;
import com.dasigochim.photo.repository.ReportPhotoRepository;
import com.dasigochim.report.domain.Report;
import com.dasigochim.report.dto.ReportResponse;
import com.dasigochim.report.service.ReportService;
import com.dasigochim.report.service.ReportViewService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {
    private final AiAnalysisClient client;
    private final ReportService reportService;
    private final ReportViewService viewService;
    private final DepartmentRepository departmentRepository;
    private final BuildingRepository buildingRepository;
    private final ReportPhotoRepository photoRepository;
    private final FileStorageService storageService;

    @Transactional(readOnly = true)
    public List<LocationCandidate> analyzeLocation(BigDecimal lat, BigDecimal lng) {
        return buildingRepository.findAll().stream().limit(5)
                .map(building -> new LocationCandidate(building.getId(), building.getName(), building.getLat(), building.getLng()))
                .toList();
    }

    @Transactional
    public ReportResponse analyzeContent(Long reportId, Long userId, String description) {
        var report = reportService.getOwnedDraft(reportId, userId);
        var result = client.analyzeContent(description, locationOf(report));
        var department = departmentRepository.findByCode(result.departmentCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        report.applyAiAnalysis(result.summary(), result.description(), result.riskLevel(), department, result.detectedHazards());
        return viewService.toResponse(report, false, false);
    }

    public AiAnalysisClient.DraftResult draftFromText(String text) {
        return client.draftFromText(text);
    }

    @Transactional(readOnly = true)
    public AiAnalysisClient.QuestionStep createReportQuestion(Long reportId, Long userId, String incidentDescription,
                                                              List<AiAnalysisClient.Answer> answers) {
        var report = reportService.getOwnedDraft(reportId, userId);
        return client.createReportQuestion(contextOf(report, incidentDescription), answers);
    }

    @Transactional
    public AiAnalysisClient.ReportDraft createReportDraft(Long reportId, Long userId, String locationDescription,
                                                           String incidentDescription,
                                                           List<AiAnalysisClient.Answer> answers) {
        var report = reportService.getOwnedDraft(reportId, userId);
        var context = new AiAnalysisClient.ReportContext(sessionId(report), locationDescription, incidentDescription,
                loadPhoto(report));
        var draft = client.createReportDraft(context, answers);
        var department = departmentRepository.findByCode(draft.departmentCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND,
                        "AI가 판정한 부서 코드를 찾을 수 없습니다: " + draft.departmentCode()));
        report.applyAiAnalysis(draft.summary(), draft.hazardContent(), draft.riskLevel(), department,
                draft.detectedHazards());
        report.applyRiskRationale(draft.riskLevelRationale(), draft.riskLevelChanged());
        return draft;
    }

    /**
     * 제출이 끝난 신고에 대해 담당자용 인사이트를 만든다.
     *
     * <p>판단 근거는 사용자가 화면에서 고친 것까지 반영된 확정 신고 내용이다.
     * 사진 판독은 캐시가 살아 있으면 재사용하고, 만료됐으면 사진으로 다시 판독한다.
     */
    @Transactional(readOnly = true)
    public AiAnalysisClient.Insight generateInsight(Report report) {
        String description = report.getDescription() == null || report.getDescription().isBlank()
                ? report.getSummary() : report.getDescription();
        return client.generateInsight(contextOf(report, description),
                new AiAnalysisClient.ConfirmedReport(report.getTrackingId(), report.getSummary(),
                        report.getDescription(), report.getRiskLevel()));
    }

    private AiAnalysisClient.ReportContext contextOf(Report report, String incidentDescription) {
        return new AiAnalysisClient.ReportContext(sessionId(report), locationOf(report), incidentDescription,
                loadPhoto(report));
    }

    /** 사진 판독 결과를 신고 단위로 재사용하려면 두 호출이 같은 세션 키를 써야 한다. */
    private String sessionId(Report report) {
        return "report-" + report.getId();
    }

    private String locationOf(Report report) {
        if (report.getLocationDescription() != null && !report.getLocationDescription().isBlank()) {
            return report.getLocationDescription();
        }
        return report.getBuildingNameSnapshot() == null ? "위치 미정" : report.getBuildingNameSnapshot();
    }

    /** 신고에 붙은 첫 사진을 바이트로 읽는다. 사진이 없으면 목 구현이 무시할 수 있도록 null 을 준다. */
    private AiAnalysisClient.Photo loadPhoto(Report report) {
        return photoRepository.findAllByReportIdOrderByDisplayOrder(report.getId()).stream()
                .findFirst()
                .map(this::readPhoto)
                .orElse(null);
    }

    private AiAnalysisClient.Photo readPhoto(ReportPhoto photo) {
        var loaded = storageService.load(photo.getUrl());
        String mimeType = photo.getMimeType() == null || photo.getMimeType().isBlank()
                ? loaded.mimeType() : photo.getMimeType();
        return new AiAnalysisClient.Photo(loaded.bytes(), mimeType);
    }

    public record LocationCandidate(Long buildingId, String name, BigDecimal lat, BigDecimal lng) {}
}
