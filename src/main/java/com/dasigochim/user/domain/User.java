package com.dasigochim.user.domain;

import com.dasigochim.building.domain.Building;
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
    /** 거주 중인 청년주택 단지. 같은 단지 입주민끼리만 신고가 보인다. */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "building_id")
    private Building building;
    @Column(length = 10)
    private String dong;
    @Column(length = 10)
    private String ho;

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

    public void updateResidence(Building building, String dong, String ho) {
        this.building = building;
        this.dong = dong;
        this.ho = ho;
    }

    public boolean isProfileCompleted() {
        return !isBlank(name) && !isBlank(phone) && building != null && !isBlank(ho);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
