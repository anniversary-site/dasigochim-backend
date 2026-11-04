package com.dasigochim.building.repository;

import com.dasigochim.building.domain.Building;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository extends JpaRepository<Building, Long> {
    List<Building> findByNameContainingIgnoreCaseOrderByName(String name);
    List<Building> findAllByOrderByName();
    java.util.Optional<Building> findByCode(String code);
}
