package com.dasigochim.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.dasigochim.admin.domain.Admin;
import com.dasigochim.admin.repository.AdminRepository;
import com.dasigochim.auth.service.AuthService;
import com.dasigochim.common.security.PrincipalType;
import com.dasigochim.department.domain.Department;
import com.dasigochim.department.repository.DepartmentRepository;
import com.dasigochim.user.domain.User;
import com.dasigochim.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired DepartmentRepository departmentRepository;

    @Test
    void googleLoginIssuesAdminTokenOnlyForLinkedAdminAccount() {
        String idToken = "admin-google-token";
        String providerId = "mock-" + java.util.UUID.nameUUIDFromBytes(
                idToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        User user = userRepository.save(new User("GOOGLE", providerId, "관리자"));
        Department department = departmentRepository.save(new Department("인증 테스트 부서", "AUTH_TEST"));
        Admin admin = adminRepository.save(new Admin("legacy-admin", "unused", "관리자", department));
        admin.linkUser(user);

        assertThat(authService.loginWithGoogle(idToken).principalType()).isEqualTo(PrincipalType.ADMIN);
        assertThat(authService.loginWithGoogle("student-google-token").principalType()).isEqualTo(PrincipalType.USER);
    }
}
