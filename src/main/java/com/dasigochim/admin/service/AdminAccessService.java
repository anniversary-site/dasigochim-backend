package com.dasigochim.admin.service;

import com.dasigochim.admin.domain.Admin;
import com.dasigochim.admin.domain.AdminRole;
import com.dasigochim.admin.domain.AdminScope;
import com.dasigochim.admin.repository.AdminRepository;
import com.dasigochim.admin.repository.AdminScopeRepository;
import com.dasigochim.building.domain.Building;
import com.dasigochim.building.repository.BuildingRepository;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import com.dasigochim.report.domain.Report;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 담당 범위. 모든 관리자 API 는 여기서 받은 범위 안의 신고·단지만 다룬다.
 * 화면에서 숨기는 것만으로는 부족하다 — 범위 밖 요청은 서버에서 403 으로 막는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAccessService {
    private final AdminRepository adminRepository;
    private final AdminScopeRepository scopeRepository;
    private final BuildingRepository buildingRepository;

    public Access access(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<AdminScope> scopes = scopeRepository.findAllByAdminId(adminId);
        if (admin.getRole() == AdminRole.SUPER) return new Access(admin, true, Set.of(), List.of());
        Set<Long> buildingIds = buildingRepository.findAll().stream()
                .filter(building -> scopes.stream().anyMatch(scope -> scope.covers(building)))
                .map(Building::getId)
                .collect(Collectors.toUnmodifiableSet());
        return new Access(admin, false, buildingIds, scopes);
    }

    public boolean covers(Admin admin, Building building) {
        if (admin.getRole() == AdminRole.SUPER) return true;
        return scopeRepository.findAllByAdminId(admin.getId()).stream().anyMatch(scope -> scope.covers(building));
    }

    /** 담당 범위 밖 신고면 403 */
    public void assertReport(Long adminId, Report report) {
        Access access = access(adminId);
        if (!access.coversBuilding(report.getBuilding() == null ? null : report.getBuilding().getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "담당 구역의 신고가 아닙니다.");
        }
    }

    /** 담당 범위 밖 단지면 403 */
    public void assertBuilding(Long adminId, Long buildingId) {
        if (!access(adminId).coversBuilding(buildingId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "담당 구역의 청년주택이 아닙니다.");
        }
    }

    /**
     * @param all         SUPER 면 true — 범위 제한 없음
     * @param buildingIds all 이 false 일 때 볼 수 있는 단지 id
     */
    public record Access(Admin admin, boolean all, Set<Long> buildingIds, List<AdminScope> scopes) {
        public boolean coversBuilding(Long buildingId) {
            return all || (buildingId != null && buildingIds.contains(buildingId));
        }

        /** "전국", "서울특별시 마포구", "공덕역 청년안심주택 e·seo(이서)" */
        public String scopeLabel() {
            if (all) return "전국";
            if (scopes.isEmpty()) return "담당 구역 없음";
            return scopes.stream().map(AdminScope::label).collect(Collectors.joining(", "));
        }
    }
}
