package com.dasigochim.auth.controller;

import com.dasigochim.admin.domain.AdminRole;
import com.dasigochim.auth.service.GuestAuthService;
import com.dasigochim.common.response.ApiResponse;
import com.dasigochim.common.security.JwtTokenProvider;
import com.dasigochim.common.security.PrincipalType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** 심사위원 게스트 로그인. {@code app.guest.enabled=true} 일 때만 노출된다. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.guest.enabled", havingValue = "true")
public class GuestAuthController {
    private final GuestAuthService guestAuthService;

    @PostMapping("/auth/guest")
    ApiResponse<JwtTokenProvider.TokenPair> guest(@Valid @RequestBody GuestLoginRequest request) {
        return ApiResponse.ok(guestAuthService.login(request.role(), request.code(), request.adminRole()));
    }

    /** adminRole 은 관리자일 때만 쓴다. 없으면 전체 관리자 */
    record GuestLoginRequest(@NotNull PrincipalType role, @NotBlank String code, AdminRole adminRole) {}
}
