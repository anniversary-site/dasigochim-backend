package com.dasigochim.report.controller;

import com.dasigochim.common.response.ApiResponse;
import com.dasigochim.common.security.SecurityPrincipal;
import com.dasigochim.photo.service.PhotoService;
import com.dasigochim.report.dto.ReportResponse;
import com.dasigochim.report.service.ReportService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;
    private final PhotoService photoService;

    @PostMapping("/draft")
    ApiResponse<ReportResponse> draft(@AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(reportService.createDraft(principal.id()));
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<ReportResponse> photos(@PathVariable Long id, @RequestPart("photos") List<MultipartFile> photos,
                                       @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(photoService.upload(id, principal.id(), photos));
    }

    @PatchMapping("/{id}/location")
    ApiResponse<ReportResponse> location(@PathVariable Long id, @RequestBody ReportService.LocationRequest request,
                                         @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(reportService.updateLocation(id, principal.id(), request));
    }

    @PatchMapping("/{id}")
    ApiResponse<ReportResponse> update(@PathVariable Long id, @RequestBody ReportService.UpdateRequest request,
                                       @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(reportService.updateDraft(id, principal.id(), request));
    }

    @GetMapping("/{id}/duplicates")
    ApiResponse<List<ReportResponse>> duplicates(@PathVariable Long id, @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(reportService.duplicates(id, principal.id()));
    }

    @PostMapping("/{id}/report-file")
    ApiResponse<ReportResponse> reportFile(@PathVariable Long id, @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(reportService.generateReportFile(id, principal.id()));
    }

    @PostMapping("/{id}/submit")
    ApiResponse<ReportResponse> submit(@PathVariable Long id, @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(reportService.submit(id, principal.id()));
    }

    @DeleteMapping("/{id}")
    ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal SecurityPrincipal principal) {
        reportService.deleteDraft(id, principal.id());
        return ApiResponse.ok();
    }

    /** 메인 화면 목록. filter = mine(내 신고) | others(같은 청년주택 다른 입주민 신고). */
    @GetMapping("/feed")
    ApiResponse<List<ReportResponse>> feed(@RequestParam(defaultValue = "mine") String filter,
                                           @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(reportService.feed(filter, principal == null ? null : principal.id()));
    }

    @GetMapping("/{trackingId}")
    ApiResponse<ReportResponse> detail(@PathVariable String trackingId,
                                       @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(reportService.findByTrackingId(trackingId, userIdOf(principal)));
    }

    private static Long userIdOf(SecurityPrincipal principal) {
        return principal == null || principal.type() != com.dasigochim.common.security.PrincipalType.USER ? null : principal.id();
    }
}
