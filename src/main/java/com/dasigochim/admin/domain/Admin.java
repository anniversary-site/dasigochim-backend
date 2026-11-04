package com.dasigochim.admin.domain;

import com.dasigochim.common.entity.BaseTimeEntity;
import com.dasigochim.department.domain.Department;
import com.dasigochim.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "admins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "login_id", unique = true, length = 50)
    private String loginId;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(nullable = false, length = 50)
    private String name;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminRole role = AdminRole.SUPER;

    public Admin(String loginId, String passwordHash, String name, Department department) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.department = department;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeRole(AdminRole role) {
        this.role = role;
    }

    public void linkUser(User user) {
        this.user = user;
    }
}
