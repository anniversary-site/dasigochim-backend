package com.dasigochim.admin.repository;

import com.dasigochim.admin.domain.AdminScope;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminScopeRepository extends JpaRepository<AdminScope, Long> {
    List<AdminScope> findAllByAdminId(Long adminId);
    void deleteAllByAdminId(Long adminId);
}
