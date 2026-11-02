package com.dasigochim.building.repository;

import com.dasigochim.building.domain.Region;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {
    List<Region> findAllByOrderBySortOrder();
}
