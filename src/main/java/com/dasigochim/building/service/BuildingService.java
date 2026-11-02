package com.dasigochim.building.service;

import com.dasigochim.building.domain.Building;
import com.dasigochim.building.repository.BuildingRepository;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingService {
    private final BuildingRepository repository;

    public List<BuildingResponse> findAll(String query) {
        List<Building> buildings = query == null || query.isBlank()
                ? repository.findAll() : repository.findByNameContainingIgnoreCaseOrderByName(query);
        return buildings.stream().map(BuildingResponse::from).toList();
    }

    public BuildingResponse findOne(Long id) {
        return BuildingResponse.from(getEntity(id));
    }

    public Building getEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.BUILDING_NOT_FOUND));
    }

    public record BuildingResponse(Long id, String name, String code, java.math.BigDecimal lat,
                                   java.math.BigDecimal lng, String address) {
        static BuildingResponse from(Building building) {
            return new BuildingResponse(building.getId(), building.getName(), building.getCode(),
                    building.getLat(), building.getLng(), building.getAddress());
        }
    }
}
