package com.dasigochim.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dasigochim.admin.domain.Admin;
import com.dasigochim.admin.domain.AdminRole;
import com.dasigochim.admin.domain.AdminScope;
import com.dasigochim.admin.repository.AdminRepository;
import com.dasigochim.admin.repository.AdminScopeRepository;
import com.dasigochim.admin.service.AdminAccessService;
import com.dasigochim.admin_report.service.AdminReportService;
import com.dasigochim.building.domain.Building;
import com.dasigochim.building.repository.BuildingRepository;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.department.domain.Department;
import com.dasigochim.department.repository.DepartmentRepository;
import com.dasigochim.report.domain.SpaceType;
import com.dasigochim.report.service.ReportService;
import com.dasigochim.user.domain.User;
import com.dasigochim.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 담당 범위: 지역 담당자 · 단지 관리인은 맡은 구역 신고만 보고 다룬다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminScopeIntegrationTest {
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired AdminScopeRepository scopeRepository;
    @Autowired AdminAccessService accessService;
    @Autowired AdminReportService adminReportService;
    @Autowired ReportService reportService;

    Building mapo;
    Building mapoOther;
    Building yongsan;
    Department department;
    Long mapoReport;
    Long mapoOtherReport;
    Long yongsanReport;

    @BeforeEach
    void setUp() {
        mapo = buildingRepository.save(new Building("마포 청년주택", "SCOPE_MAPO", "서울", "서울특별시", "마포구"));
        mapoOther = buildingRepository.save(new Building("마포 다른 청년주택", "SCOPE_MAPO_2", "서울", "서울특별시", "마포구"));
        yongsan = buildingRepository.save(new Building("용산 청년주택", "SCOPE_YONGSAN", "서울", "서울특별시", "용산구"));
        department = departmentRepository.save(new Department("범위 테스트 부서", "SCOPE_TEST"));
        User reporter = new User("GOOGLE", "scope-reporter", "범위 신고자");
        reporter.updateResidence(mapo, null, "301");
        userRepository.save(reporter);
        mapoReport = submit(reporter, mapo);
        mapoOtherReport = submit(reporter, mapoOther);
        yongsanReport = submit(reporter, yongsan);
    }

    @Test
    void superAdminSeesEverything() {
        Long adminId = admin("전체", AdminRole.SUPER).getId();
        assertThat(ids(adminId)).contains(mapoReport, mapoOtherReport, yongsanReport);
    }

    @Test
    void regionAdminSeesOnlyTheirDistrict() {
        Admin admin = admin("마포구 담당", AdminRole.REGION);
        scopeRepository.save(AdminScope.region(admin, "서울특별시", "마포구"));

        assertThat(ids(admin.getId())).contains(mapoReport, mapoOtherReport).doesNotContain(yongsanReport);
        assertThat(adminReportService.detail(mapoReport, admin.getId()).id()).isEqualTo(mapoReport);
        assertThatThrownBy(() -> adminReportService.detail(yongsanReport, admin.getId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("담당 구역");
        assertThatThrownBy(() -> accessService.assertBuilding(admin.getId(), yongsan.getId()))
                .isInstanceOf(BusinessException.class);
        assertThat(adminReportService.me(admin.getId()).scopeLabel()).isEqualTo("서울특별시 마포구");
    }

    @Test
    void buildingAdminSeesOnlyTheirComplex() {
        Admin admin = admin("마포 관리인", AdminRole.BUILDING);
        scopeRepository.save(AdminScope.building(admin, mapo));

        assertThat(ids(admin.getId())).contains(mapoReport).doesNotContain(mapoOtherReport, yongsanReport);
        assertThatThrownBy(() -> adminReportService.changeStatus(mapoOtherReport, admin.getId(),
                com.dasigochim.report.domain.ReportStatus.REVIEWING)).isInstanceOf(BusinessException.class);
        var me = adminReportService.me(admin.getId());
        assertThat(me.role()).isEqualTo("BUILDING");
        assertThat(me.scopes()).singleElement().satisfies(scope -> assertThat(scope.buildingId()).isEqualTo(mapo.getId()));
    }

    @Test
    void scopedAdminWithoutScopesSeesNothing() {
        Long adminId = admin("미배정", AdminRole.REGION).getId();
        assertThat(ids(adminId)).isEmpty();
    }

    private java.util.List<Long> ids(Long adminId) {
        return adminReportService.list(adminId, null, null, null, 0, 100, "latest").content().stream()
                .map(r -> r.id()).toList();
    }

    private Admin admin(String name, AdminRole role) {
        Admin admin = new Admin("scope-" + name, "hash", name, department);
        admin.changeRole(role);
        return adminRepository.save(admin);
    }

    private Long submit(User reporter, Building building) {
        Long reportId = reportService.createDraft(reporter.getId()).id();
        reportService.updateLocation(reportId, reporter.getId(), new ReportService.LocationRequest(
                building.getId(), building.getName() + " 1층 로비", null, null, null, null, null,
                SpaceType.COMMON, null, "로비", null, null, null, null, null, null));
        reportService.submit(reportId, reporter.getId());
        return reportId;
    }
}
