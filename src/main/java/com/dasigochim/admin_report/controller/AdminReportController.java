package com.dasigochim.admin_report.controller;

import com.dasigochim.admin_report.service.AdminReportService;
import com.dasigochim.common.response.ApiResponse;
import com.dasigochim.common.response.PageResponse;
import com.dasigochim.common.security.SecurityPrincipal;
import com.dasigochim.insight.dto.ReportInsightResponse;
import com.dasigochim.insight.service.ReportInsightService;
import com.dasigochim.report.domain.ReportStatus;
import com.dasigochim.report.domain.RiskLevel;
import com.dasigochim.report.dto.ReportResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminReportController {
    private final AdminReportService service;
    private final ReportInsightService insightService;

    @GetMapping("/stats/summary")
    ApiResponse<AdminReportService.StatsResponse> stats() { return ApiResponse.ok(service.stats()); }

    @GetMapping("/me")
    ApiResponse<AdminReportService.AdminMeResponse> me(@AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(service.me(principal.id()));
    }

    @GetMapping("/departments")
    ApiResponse<List<AdminReportService.DepartmentPendingResponse>> departments() {
        return ApiResponse.ok(service.departments());
    }

    @GetMapping("/reports")
    ApiResponse<PageResponse<ReportResponse>> reports(@RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) Long departmentId, @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort) {
        return ApiResponse.ok(service.list(status, departmentId, query, page, size, sort));
    }

    @GetMapping("/reports/{id}")
    ApiResponse<ReportResponse> detail(@PathVariable Long id) { return ApiResponse.ok(service.detail(id)); }

    @GetMapping("/reports/{id}/report-file")
    ApiResponse<FileResponse> reportFile(@PathVariable Long id) { return ApiResponse.ok(new FileResponse(service.reportFile(id))); }

    @PostMapping("/reports/{id}/report-file")
    ApiResponse<FileResponse> generateReportFile(@PathVariable Long id) {
        return ApiResponse.ok(new FileResponse(service.generateReportFile(id)));
    }

    /** 위험도 판단 근거(④)와 담당자용 인사이트(⑤). 인사이트는 생성 전이면 null 이다. */
    @GetMapping("/reports/{id}/insight")
    ApiResponse<ReportInsightResponse> insight(@PathVariable Long id) {
        return ApiResponse.ok(insightService.find(id));
    }

    /** 인사이트 재생성. 제출 직후 비동기 생성이 실패했거나 내용을 갱신할 때 쓴다. */
    @PostMapping("/reports/{id}/insight")
    ApiResponse<ReportInsightResponse> regenerateInsight(@PathVariable Long id) {
        return ApiResponse.ok(insightService.generate(id));
    }

    @PatchMapping("/reports/{id}/status")
    ApiResponse<ReportResponse> status(@PathVariable Long id, @Valid @RequestBody StatusRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(service.changeStatus(id, principal.id(), request.status()));
    }

    @PatchMapping("/reports/{id}/department")
    ApiResponse<ReportResponse> department(@PathVariable Long id, @Valid @RequestBody DepartmentRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(service.reassign(id, principal.id(), request.departmentId()));
    }

    @PatchMapping("/reports/{id}/risk-level")
    ApiResponse<ReportResponse> risk(@PathVariable Long id, @Valid @RequestBody RiskRequest request) {
        return ApiResponse.ok(service.adjustRisk(id, request.riskLevel()));
    }

    @PostMapping("/reports/{id}/resolve")
    ApiResponse<ReportResponse> resolve(@PathVariable Long id, @Valid @RequestBody ResolveRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(service.resolve(id, principal.id(), request.actionNote()));
    }

    record StatusRequest(@NotNull ReportStatus status) {}
    record DepartmentRequest(@NotNull Long departmentId) {}
    record RiskRequest(@NotNull RiskLevel riskLevel) {}
    record ResolveRequest(@NotBlank String actionNote) {}
    record FileResponse(String url) {}
}
