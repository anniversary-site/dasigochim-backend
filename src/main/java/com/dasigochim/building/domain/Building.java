package com.dasigochim.building.domain;

import com.dasigochim.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "buildings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Building extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(unique = true, length = 30)
    private String code;
    @Column(precision = 10, scale = 7)
    private BigDecimal lat;
    @Column(precision = 10, scale = 7)
    private BigDecimal lng;
    private String address;

    public Building(String name, String code, BigDecimal lat, BigDecimal lng, String address) {
        this.name = name;
        this.code = code;
        this.lat = lat;
        this.lng = lng;
        this.address = address;
    }
}
