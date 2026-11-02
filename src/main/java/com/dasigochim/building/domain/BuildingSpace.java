package com.dasigochim.building.domain;

import com.dasigochim.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 단지의 공용공간(주차장, 피트니스 등)과 그 공간을 보여줄 3D 장면. */
@Getter
@Entity
@Table(name = "building_spaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuildingSpace extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "building_id", nullable = false)
    private Building building;
    @Column(nullable = false, length = 30)
    private String code;
    @Column(nullable = false, length = 60)
    private String name;
    @Column(name = "floor_label", length = 20)
    private String floorLabel;
    @Column(name = "scene_id", nullable = false, length = 40)
    private String sceneId;
    /** 여러 층이 한 장면을 쓰는 공간(4~18층 복도 등)은 층을 따로 받는다. */
    @Column(name = "requires_floor", nullable = false)
    private boolean requiresFloor;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public BuildingSpace(Building building, String code, String name, String floorLabel, String sceneId,
                         boolean requiresFloor, int sortOrder) {
        this.building = building;
        this.code = code;
        this.name = name;
        this.floorLabel = floorLabel;
        this.sceneId = sceneId;
        this.requiresFloor = requiresFloor;
        this.sortOrder = sortOrder;
    }
}
