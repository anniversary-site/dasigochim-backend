package com.dasigochim.department.domain;

import com.dasigochim.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "departments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String name;
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    /** C-2 응답에 실려 신고서 초안에 노출된다. */
    @Column(length = 50)
    private String contact;
    /** false 면 C-1 후보에서 제외되고 C-2 는 기본 부서로 강등한다. */
    @Column(nullable = false)
    private boolean active = true;

    public Department(String name, String code) {
        this.name = name;
        this.code = code;
    }
}
