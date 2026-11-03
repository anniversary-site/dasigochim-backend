package com.dasigochim.building.repository;

import com.dasigochim.building.domain.BuildingUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingUnitRepository extends JpaRepository<BuildingUnit, Long> {
    List<BuildingUnit> findAllByBuildingIdAndHo(Long buildingId, String ho);
    Optional<BuildingUnit> findFirstByOrderByIdAsc();
    boolean existsByBuildingId(Long buildingId);
    List<BuildingUnit> findAllByBuildingId(Long buildingId);

    /** 세대 표(=3D 평면)가 있는 단지 id. */
    @org.springframework.data.jpa.repository.Query("select distinct u.building.id from BuildingUnit u")
    java.util.Set<Long> findBuildingIdsWithUnits();
}
