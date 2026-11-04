package com.dasigochim.admin.domain;

import com.dasigochim.building.domain.Building;
import com.dasigochim.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자가 맡은 구역 하나. 시·도(+시·군·구) 또는 청년주택 하나. */
@Getter
@Entity
@Table(name = "admin_scopes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminScope extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;
    @Column(length = 30)
    private String sido;
    @Column(length = 30)
    private String sigungu;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    private AdminScope(Admin admin, String sido, String sigungu, Building building) {
        this.admin = admin;
        this.sido = sido;
        this.sigungu = sigungu;
        this.building = building;
    }

    public static AdminScope region(Admin admin, String sido, String sigungu) {
        return new AdminScope(admin, sido, sigungu, null);
    }

    public static AdminScope building(Admin admin, Building building) {
        return new AdminScope(admin, null, null, building);
    }

    public boolean covers(Building target) {
        if (target == null) return false;
        if (building != null) return building.getId().equals(target.getId());
        if (sido == null || !sido.equals(target.getSido())) return false;
        return sigungu == null || sigungu.equals(target.getSigungu());
    }

    /** "서울특별시 마포구", "공덕역 청년안심주택 e·seo(이서)" */
    public String label() {
        if (building != null) return building.getName();
        return sigungu == null ? sido + " 전체" : sido + " " + sigungu;
    }
}
