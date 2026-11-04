package com.dasigochim.auth.service;

import com.dasigochim.admin.domain.Admin;
import com.dasigochim.admin.domain.AdminRole;
import com.dasigochim.admin.domain.AdminScope;
import com.dasigochim.admin.repository.AdminScopeRepository;
import com.dasigochim.building.repository.BuildingRepository;
import com.dasigochim.admin.repository.AdminRepository;
import com.dasigochim.building.repository.BuildingUnitRepository;
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
    static final String RESIDENT_NAME = "김가명";
    static final String ADMIN_NAME = "테스트 관리자";
    /** 시연용 지역 담당자 · 단지 관리인 */
    static final String REGION_ADMIN_PROVIDER_ID = "judge-admin-region";
    static final String BUILDING_ADMIN_PROVIDER_ID = "judge-admin-building";
    static final String REGION_ADMIN_NAME = "마포구 담당자";
    static final String BUILDING_ADMIN_NAME = "공덕역 관리인";
    static final String REGION_SIDO = "서울특별시";
    static final String REGION_SIGUNGU = "마포구";
    static final String BUILDING_CODE = "ESEO314";
    /** 예전 기본 이름. 이 이름 그대로인 계정만 새 이름으로 바꾼다 (프로필에서 바꾼 이름은 둔다). */
    private static final String OLD_RESIDENT_NAME = "심사위원";
    private static final String OLD_ADMIN_NAME = "심사위원 관리자";

    private final UserRepository userRepository;
    private final BuildingUnitRepository unitRepository;
    private final AdminRepository adminRepository;
    private final AdminScopeRepository scopeRepository;
    private final BuildingRepository buildingRepository;
    private final DepartmentRepository departmentRepository;
    private final JwtTokenProvider tokenProvider;

    @Value("${app.guest.access-code:}")
    private String accessCode;
    @Value("${app.guest.admin-department-code:FACILITY}")
    private String adminDepartmentCode;

    @Transactional
    public JwtTokenProvider.TokenPair login(PrincipalType role, String code) {
        return login(role, code, AdminRole.SUPER);
    }

    /** adminRole: 관리자로 들어갈 때 전체 관리자 / 지역 담당자(마포구) / 단지 관리인(공덕역 e·seo) 중 하나 */
    @Transactional
    public JwtTokenProvider.TokenPair login(PrincipalType role, String code, AdminRole adminRole) {
        verify(code);
        if (role == PrincipalType.ADMIN) {
            return tokenProvider.issue(guestAdmin(adminRole == null ? AdminRole.SUPER : adminRole).getId(), PrincipalType.ADMIN);
        }
        return tokenProvider.issue(guestStudent().getId(), PrincipalType.USER);
    }

    private void verify(String code) {
        if (accessCode == null || accessCode.isBlank() || code == null
                || !MessageDigest.isEqual(accessCode.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "테스트 로그인 접근 코드가 올바르지 않습니다.");
        }
    }

    private User guestStudent() {
        User user = userRepository.findByProviderAndProviderId(PROVIDER, STUDENT_PROVIDER_ID).orElseGet(() -> {
            User created = new User(PROVIDER, STUDENT_PROVIDER_ID, RESIDENT_NAME);
            created.updateProfile(RESIDENT_NAME, null, null, "010-0000-0000");
            return userRepository.save(created);
        });
        if (OLD_RESIDENT_NAME.equals(user.getName())) {
            user.updateProfile(RESIDENT_NAME, user.getMajor(), user.getStudentNo(), user.getPhone());
        }
        // 신고 진입 가드(프로필 필수)를 통과하도록 체험용 거주 세대를 채워 둔다.
        if (user.getBuilding() == null) {
            unitRepository.findFirstByOrderByIdAsc()
                    .ifPresent(unit -> user.updateResidence(unit.getBuilding(), unit.getDong(), unit.getHo()));
        }
        return user;
    }

    private Admin guestAdmin(AdminRole adminRole) {
        String providerId = switch (adminRole) {
            case SUPER -> ADMIN_PROVIDER_ID;
            case REGION -> REGION_ADMIN_PROVIDER_ID;
            case BUILDING -> BUILDING_ADMIN_PROVIDER_ID;
        };
        String name = switch (adminRole) {
            case SUPER -> ADMIN_NAME;
            case REGION -> REGION_ADMIN_NAME;
            case BUILDING -> BUILDING_ADMIN_NAME;
        };
        User user = userRepository.findByProviderAndProviderId(PROVIDER, providerId)
                .orElseGet(() -> userRepository.save(new User(PROVIDER, providerId, name)));
        if (OLD_ADMIN_NAME.equals(user.getName())) {
            user.updateProfile(name, user.getMajor(), user.getStudentNo(), user.getPhone());
        }
        Admin found = adminRepository.findByUserId(user.getId()).orElseGet(() -> {
            var department = departmentRepository.findByCode(adminDepartmentCode)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
            Admin admin = new Admin(null, null, name, department);
            admin.linkUser(user);
            return adminRepository.save(admin);
        });
        if (OLD_ADMIN_NAME.equals(found.getName())) found.rename(name);
        found.changeRole(adminRole);
        // 담당 구역은 로그인할 때마다 시연용 값으로 맞춘다.
        scopeRepository.deleteAllByAdminId(found.getId());
        scopeRepository.flush();
        if (adminRole == AdminRole.REGION) {
            scopeRepository.save(AdminScope.region(found, REGION_SIDO, REGION_SIGUNGU));
        } else if (adminRole == AdminRole.BUILDING) {
            buildingRepository.findByCode(BUILDING_CODE)
                    .or(() -> unitRepository.findFirstByOrderByIdAsc().map(unit -> unit.getBuilding()))
                    .ifPresent(building -> scopeRepository.save(AdminScope.building(found, building)));
        }
        return found;
    }
}
