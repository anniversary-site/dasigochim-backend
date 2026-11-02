package com.dasigochim.user.controller;

import com.dasigochim.common.response.ApiResponse;
import com.dasigochim.common.security.SecurityPrincipal;
import com.dasigochim.report.dto.ReportResponse;
import com.dasigochim.user.service.MeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {
    private final MeService service;

    @GetMapping("/profile")
    ApiResponse<MeService.ProfileResponse> profile(@AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(service.profile(principal.id()));
    }

    @PatchMapping("/profile")
    ApiResponse<MeService.ProfileResponse> updateProfile(@Valid @RequestBody ProfileUpdateRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(service.updateProfile(principal.id(), request.name(), request.phone(),
                request.buildingId(), request.dong(), request.ho()));
    }

    @GetMapping("/reports")
    ApiResponse<List<ReportResponse>> reports(@AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(service.reports(principal.id()));
    }

    @GetMapping("/notifications")
    ApiResponse<List<MeService.NotificationResponse>> notifications(@AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(service.notifications(principal.id()));
    }

    @PatchMapping("/notifications/{id}/read")
    ApiResponse<Void> read(@PathVariable Long id, @AuthenticationPrincipal SecurityPrincipal principal) {
        service.readNotification(principal.id(), id);
        return ApiResponse.ok();
    }

    record ProfileUpdateRequest(
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "^[0-9+() -]{7,20}$", message = "연락처 형식이 올바르지 않습니다.") String phone,
            @NotNull Long buildingId,
            String dong,
            @NotBlank String ho) {}
}
