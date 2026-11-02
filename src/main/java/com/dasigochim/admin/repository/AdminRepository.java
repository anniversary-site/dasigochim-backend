package com.dasigochim.admin.repository;

import com.dasigochim.admin.domain.Admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByLoginId(String loginId);
    Optional<Admin> findByUserId(Long userId);
}
