package com.dasigochim.auth.service;

import com.dasigochim.admin.domain.Admin;
import com.dasigochim.admin.repository.AdminRepository;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import com.dasigochim.common.security.JwtTokenProvider;
import com.dasigochim.common.security.PrincipalType;
import com.dasigochim.department.repository.DepartmentRepository;
import com.dasigochim.user.domain.User;
import com.dasigochim.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 심사위원용 게스트 로그인. Google 계정 없이 접근 코드로 체험 계정 JWT를 발급한다.
 *
 * <p>{@code app.guest.enabled=true} 일 때만 빈이 생성된다. 게스트 계정은 provider 가 {@code GUEST} 라
 * Google 로그인 계정과 절대 겹치지 않으며, 최초 호출 때 직접 만든다(마이그레이션으로 운영 DB 에 심지 않는다).
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.guest.enabled", havingValue = "true")
public class GuestAuthService {
    static final String PROVIDER = "GUEST";
    static final String STUDENT_PROVIDER_ID = "judge-student";
    static final String ADMIN_PROVIDER_ID = "judge-admin";

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final DepartmentRepository departmentRepository;
    private final JwtTokenProvider tokenProvider;

    @Value("${app.guest.access-code:}")
    private String accessCode;
    @Value("${app.guest.admin-department-code:FACILITY}")
    private String adminDepartmentCode;

    @Transactional
    public JwtTokenProvider.TokenPair login(PrincipalType role, String code) {
        verify(code);
        if (role == PrincipalType.ADMIN) {
            return tokenProvider.issue(guestAdmin().getId(), PrincipalType.ADMIN);
        }
        return tokenProvider.issue(guestStudent().getId(), PrincipalType.USER);
    }

    private void verify(String code) {
        if (accessCode == null || accessCode.isBlank() || code == null
                || !MessageDigest.isEqual(accessCode.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "심사위원 접근 코드가 올바르지 않습니다.");
        }
    }

    private User guestStudent() {
        return userRepository.findByProviderAndProviderId(PROVIDER, STUDENT_PROVIDER_ID).orElseGet(() -> {
            User user = new User(PROVIDER, STUDENT_PROVIDER_ID, "심사위원");
            // 신고 진입 가드(프로필 필수)를 통과하도록 체험용 프로필을 채워 둔다.
            user.updateProfile("심사위원", "체험 계정", "00000000", "010-0000-0000");
            return userRepository.save(user);
        });
    }

    private Admin guestAdmin() {
        User user = userRepository.findByProviderAndProviderId(PROVIDER, ADMIN_PROVIDER_ID)
                .orElseGet(() -> userRepository.save(new User(PROVIDER, ADMIN_PROVIDER_ID, "심사위원 관리자")));
        return adminRepository.findByUserId(user.getId()).orElseGet(() -> {
            var department = departmentRepository.findByCode(adminDepartmentCode)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
            Admin admin = new Admin(null, null, "심사위원 관리자", department);
            admin.linkUser(user);
            return adminRepository.save(admin);
        });
    }
}
