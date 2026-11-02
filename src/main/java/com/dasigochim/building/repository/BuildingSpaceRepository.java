package com.dasigochim.building.repository;

import com.dasigochim.building.domain.BuildingSpace;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingSpaceRepository extends JpaRepository<BuildingSpace, Long> {
    List<BuildingSpace> findAllByBuildingIdOrderBySortOrder(Long buildingId);
    Optional<BuildingSpace> findByBuildingIdAndCode(Long buildingId, String code);
}
