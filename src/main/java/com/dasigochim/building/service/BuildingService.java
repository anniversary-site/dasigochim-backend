package com.dasigochim.building.service;

import com.dasigochim.building.domain.Building;
import com.dasigochim.building.domain.BuildingSpace;
import com.dasigochim.building.domain.BuildingUnit;
import com.dasigochim.building.repository.BuildingRepository;
import com.dasigochim.building.repository.BuildingSpaceRepository;
import com.dasigochim.building.repository.BuildingUnitRepository;
import com.dasigochim.building.repository.RegionRepository;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final RegionRepository regionRepository;

    public List<BuildingResponse> findAll(String query, String sido, String sigungu) {
        List<Building> buildings = query == null || query.isBlank()
                ? repository.findAllByOrderByName() : repository.findByNameContainingIgnoreCaseOrderByName(query);
        var withUnits = unitRepository.findBuildingIdsWithUnits();
        return buildings.stream()
                .filter(building -> isBlank(sido) || sido.equals(building.getSido()))
                .filter(building -> isBlank(sigungu) || sigungu.equals(building.getSigungu()))
                .map(building -> BuildingResponse.from(building, withUnits.contains(building.getId()))).toList();
    }

    /** 전국 시·도와 그 아래 시·군·구 목록. 청년주택이 없는 지역도 들어 있다. */
    public List<RegionResponse> regions() {
        Map<String, List<String>> regions = new LinkedHashMap<>();
        regionRepository.findAllByOrderBySortOrder()
                .forEach(region -> regions.computeIfAbsent(region.getSido(), key -> new java.util.ArrayList<>()).add(region.getSigungu()));
        return regions.entrySet().stream()
                .map(entry -> new RegionResponse(entry.getKey(), List.copyOf(entry.getValue()))).toList();
    }

    public BuildingResponse findOne(Long id) {
        return BuildingResponse.from(getEntity(id), hasUnits(id));
    }

    /** 세대 표가 있는 단지만 동·호수로 세대 타입과 3D 평면을 찾을 수 있다. */
    public boolean hasUnits(Long buildingId) {
        return unitRepository.existsByBuildingId(buildingId);
    }

    public List<SpaceResponse> spaces(Long buildingId) {
        getEntity(buildingId);
        return spaceRepository.findAllByBuildingIdOrderBySortOrder(buildingId).stream().map(SpaceResponse::from).toList();
    }

    /** 단지의 세대 전체(동 · 층 · 호수 순). 신고 화면에서 호수를 입력하는 동안 바로 확인하는 데 쓴다. */
    public List<UnitResponse> units(Long buildingId) {
        getEntity(buildingId);
        return unitRepository.findAllByBuildingId(buildingId).stream()
                .sorted(java.util.Comparator
                        .comparing((BuildingUnit unit) -> unit.getDong() == null ? "" : unit.getDong())
                        .thenComparingInt(unit -> numberOrMax(unit.getFloor()))
                        .thenComparing(BuildingUnit::getHo))
                .map(UnitResponse::from)
                .toList();
    }

    private static int numberOrMax(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
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
    public static String normalize(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[동호\\s]", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** has3d = 세대 표와 3D 평면이 있어 3D 로 위치를 찍을 수 있는 단지 */
    public record BuildingResponse(Long id, String name, String code, java.math.BigDecimal lat,
                                   java.math.BigDecimal lng, String address, String sido, String sigungu,
                                   String supplyStatus, boolean has3d) {
        static BuildingResponse from(Building building, boolean has3d) {
            return new BuildingResponse(building.getId(), building.getName(), building.getCode(),
                    building.getLat(), building.getLng(), building.getAddress(), building.getSido(), building.getSigungu(),
                    building.getSupplyStatus(), has3d);
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
