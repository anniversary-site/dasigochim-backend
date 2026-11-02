package com.dasigochim.user.domain;

import com.dasigochim.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String provider;
    @Column(name = "provider_id", nullable = false, length = 128)
    private String providerId;
    @Column(nullable = false, length = 50)
    private String name;
    @Column(length = 100)
    private String major;
    @Column(name = "student_no", length = 20)
    private String studentNo;
    @Column(length = 20)
    private String phone;
    @Column(name = "push_token")
    private String pushToken;

    public User(String provider, String providerId, String name) {
        this.provider = provider;
        this.providerId = providerId;
        this.name = name;
    }

    public void updateProfile(String name, String major, String studentNo, String phone) {
        this.name = name;
        this.major = major;
        this.studentNo = studentNo;
        this.phone = phone;
    }

    public boolean isProfileCompleted() {
        return !isBlank(name) && !isBlank(major) && !isBlank(studentNo) && !isBlank(phone);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
