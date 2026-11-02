package com.dasigochim.notification.controller;

import com.dasigochim.common.response.ApiResponse;
import com.dasigochim.common.security.SecurityPrincipal;
import com.dasigochim.notification.service.AdminNotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {
    private final AdminNotificationService service;

    @GetMapping
    ApiResponse<List<AdminNotificationService.NotificationResponse>> notifications(
            @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(service.notifications(principal.id()));
    }

    @PatchMapping("/{id}/read")
    ApiResponse<Void> read(@PathVariable Long id, @AuthenticationPrincipal SecurityPrincipal principal) {
        service.readNotification(principal.id(), id);
        return ApiResponse.ok();
    }
}
