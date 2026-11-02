package com.dasigochim.building.domain;

import com.dasigochim.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 단지 안의 한 세대. 호수로 세대 타입과 그 타입의 3D 장면을 찾는다. */
@Getter
@Entity
@Table(name = "building_units")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuildingUnit extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "building_id", nullable = false)
    private Building building;
    @Column(length = 10)
    private String dong;
    @Column(nullable = false, length = 10)
    private String ho;
    @Column(nullable = false, length = 10)
    private String floor;
    @Column(name = "unit_type", nullable = false, length = 10)
    private String unitType;
    @Column(name = "scene_id", nullable = false, length = 40)
    private String sceneId;

    public BuildingUnit(Building building, String dong, String ho, String floor, String unitType, String sceneId) {
        this.building = building;
        this.dong = dong;
        this.ho = ho;
        this.floor = floor;
        this.unitType = unitType;
        this.sceneId = sceneId;
    }
}
