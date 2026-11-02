package com.dasigochim.building.repository;

import com.dasigochim.building.domain.BuildingUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingUnitRepository extends JpaRepository<BuildingUnit, Long> {
    List<BuildingUnit> findAllByBuildingIdAndHo(Long buildingId, String ho);
    Optional<BuildingUnit> findFirstByOrderByIdAsc();
}
