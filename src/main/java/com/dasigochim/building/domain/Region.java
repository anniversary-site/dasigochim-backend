package com.dasigochim.building.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 전국 시·도 / 시·군·구. 신고 지역 선택 목록이다. */
@Getter
@Entity
@Table(name = "regions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String sido;
    @Column(nullable = false, length = 30)
    private String sigungu;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Region(String sido, String sigungu, int sortOrder) {
        this.sido = sido;
        this.sigungu = sigungu;
        this.sortOrder = sortOrder;
    }
}
