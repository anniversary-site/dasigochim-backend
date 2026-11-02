package com.dasigochim.building.service;

import com.dasigochim.building.domain.Building;
import com.dasigochim.building.domain.BuildingSpace;
import com.dasigochim.building.domain.BuildingUnit;
import com.dasigochim.building.repository.BuildingRepository;
import com.dasigochim.building.repository.BuildingSpaceRepository;
import com.dasigochim.building.repository.BuildingUnitRepository;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingService {
    private final BuildingRepository repository;
    private final BuildingUnitRepository unitRepository;
    private final BuildingSpaceRepository spaceRepository;

    public List<BuildingResponse> findAll(String query, String sido, String sigungu) {
        List<Building> buildings = query == null || query.isBlank()
                ? repository.findAllByOrderByName() : repository.findByNameContainingIgnoreCaseOrderByName(query);
        return buildings.stream()
                .filter(building -> isBlank(sido) || sido.equals(building.getSido()))
                .filter(building -> isBlank(sigungu) || sigungu.equals(building.getSigungu()))
                .map(BuildingResponse::from).toList();
    }

    /** 청년주택이 있는 시·도와 그 아래 시·군·구 목록. */
    public List<RegionResponse> regions() {
        Map<String, TreeSet<String>> regions = new LinkedHashMap<>();
        repository.findAllByOrderByName().stream()
                .filter(building -> !isBlank(building.getSido()))
                .sorted(java.util.Comparator.comparing(Building::getSido))
                .forEach(building -> {
                    var sigungus = regions.computeIfAbsent(building.getSido(), key -> new TreeSet<>());
                    if (!isBlank(building.getSigungu())) sigungus.add(building.getSigungu());
                });
        return regions.entrySet().stream()
                .map(entry -> new RegionResponse(entry.getKey(), List.copyOf(entry.getValue()))).toList();
    }

    public BuildingResponse findOne(Long id) {
        return BuildingResponse.from(getEntity(id));
    }

    public List<SpaceResponse> spaces(Long buildingId) {
        getEntity(buildingId);
        return spaceRepository.findAllByBuildingIdOrderBySortOrder(buildingId).stream().map(SpaceResponse::from).toList();
    }

    public UnitResponse lookupUnit(Long buildingId, String dong, String ho) {
        return UnitResponse.from(findUnit(buildingId, dong, ho));
    }

    public BuildingUnit findUnit(Long buildingId, String dong, String ho) {
        getEntity(buildingId);
        String normalizedHo = normalize(ho);
        if (normalizedHo == null) throw new BusinessException(ErrorCode.UNIT_NOT_FOUND);
        List<BuildingUnit> units = unitRepository.findAllByBuildingIdAndHo(buildingId, normalizedHo);
        String normalizedDong = normalize(dong);
        // 동이 하나뿐인 단지는 동을 비워 두고 호수만으로 찾는다.
        return units.stream()
                .filter(unit -> unit.getDong() == null || normalizedDong == null || Objects.equals(unit.getDong(), normalizedDong))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNIT_NOT_FOUND));
    }

    public BuildingSpace findSpace(Long buildingId, String code) {
        return spaceRepository.findByBuildingIdAndCode(buildingId, code)
                .orElseThrow(() -> new BusinessException(ErrorCode.SPACE_NOT_FOUND));
    }

    public Building getEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.BUILDING_NOT_FOUND));
    }

    /** "1203호", " 1203 " 처럼 들어와도 숫자·영문만 남긴다. */
    private static String normalize(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[동호\\s]", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record BuildingResponse(Long id, String name, String code, java.math.BigDecimal lat,
                                   java.math.BigDecimal lng, String address, String sido, String sigungu) {
        static BuildingResponse from(Building building) {
            return new BuildingResponse(building.getId(), building.getName(), building.getCode(),
                    building.getLat(), building.getLng(), building.getAddress(), building.getSido(), building.getSigungu());
        }
    }

    public record RegionResponse(String sido, List<String> sigungus) {}

    public record SpaceResponse(String code, String name, String floorLabel, String sceneId, boolean requiresFloor) {
        static SpaceResponse from(BuildingSpace space) {
            return new SpaceResponse(space.getCode(), space.getName(), space.getFloorLabel(), space.getSceneId(),
                    space.isRequiresFloor());
        }
    }

    public record UnitResponse(String dong, String ho, String floor, String unitType, String sceneId) {
        static UnitResponse from(BuildingUnit unit) {
            return new UnitResponse(unit.getDong(), unit.getHo(), unit.getFloor(), unit.getUnitType(), unit.getSceneId());
        }
    }
}
