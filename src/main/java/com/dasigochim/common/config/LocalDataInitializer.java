package com.dasigochim.common.config;

import com.dasigochim.admin.domain.Admin;
import com.dasigochim.admin.repository.AdminRepository;
import com.dasigochim.department.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {
    private final AdminRepository adminRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (adminRepository.findByLoginId("admin").isEmpty()) {
            departmentRepository.findByCode("FACILITY").ifPresent(department ->
                    adminRepository.save(new Admin("admin", passwordEncoder.encode("admin1234"), "로컬 관리자", department)));
        }
    }
}
