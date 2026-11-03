package com.dasigochim.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dasigochim.admin.repository.AdminRepository;
import com.dasigochim.auth.service.GuestAuthService;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.security.PrincipalType;
import com.dasigochim.department.domain.Department;
import com.dasigochim.department.repository.DepartmentRepository;
import com.dasigochim.user.repository.UserRepository;
import com.dasigochim.building.domain.Building;
import com.dasigochim.building.domain.BuildingUnit;
import com.dasigochim.building.repository.BuildingRepository;
import com.dasigochim.building.repository.BuildingUnitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GuestAuthServiceTest {
    @Autowired GuestAuthService guestAuthService;
    @Autowired UserRepository userRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired BuildingUnitRepository unitRepository;

    @Test
    void issuesStudentAndAdminTokensWithValidCodeAndReusesAccounts() {
        departmentRepository.save(new Department("게스트 테스트 부서", "GUEST_TEST"));
        Building building = buildingRepository.save(new Building("테스트 청년주택", "TEST_HOUSING", "서울", "서울특별시", "마포구"));
        unitRepository.save(new BuildingUnit(building, null, "301", "3", "18A", "unit_18A-type"));

        var student = guestAuthService.login(PrincipalType.USER, "test-guest-code");
        var admin = guestAuthService.login(PrincipalType.ADMIN, "test-guest-code");
        guestAuthService.login(PrincipalType.ADMIN, "test-guest-code");

        assertThat(student.principalType()).isEqualTo(PrincipalType.USER);
        assertThat(admin.principalType()).isEqualTo(PrincipalType.ADMIN);
        assertThat(userRepository.findByProviderAndProviderId("GUEST", "judge-student")).get()
                .satisfies(user -> {
                    assertThat(user.isProfileCompleted()).isTrue();
                    assertThat(user.getName()).isEqualTo("김가명");
                });
        assertThat(adminRepository.findAll()).singleElement()
                .satisfies(found -> assertThat(found.getName()).isEqualTo("테스트 관리자"));
        assertThat(adminRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsWrongOrMissingCode() {
        assertThatThrownBy(() -> guestAuthService.login(PrincipalType.ADMIN, "wrong"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guestAuthService.login(PrincipalType.USER, null))
                .isInstanceOf(BusinessException.class);
    }
}
