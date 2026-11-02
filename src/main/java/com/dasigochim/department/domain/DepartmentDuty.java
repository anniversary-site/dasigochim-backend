package com.dasigochim.department.domain;

import com.dasigochim.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부서 업무범위 문장 하나. C-1 검색의 후보 단위다.
 *
 * <p>후보 1건 = 부서가 아니라 <b>문장 1개</b>다. 같은 부서가 여러 번 후보에 오를 수 있고,
 * LLM 은 어느 문장이 근거인지 인용해야 하므로 문장 단위로 쪼개 둔다.
 */
@Getter
@Entity
@Table(name = "department_duties")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepartmentDuty extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "duty_text", nullable = false, columnDefinition = "TEXT")
    private String dutyText;

    public DepartmentDuty(Department department, String dutyText) {
        this.department = department;
        this.dutyText = dutyText;
    }
}
