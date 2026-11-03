package com.dasigochim.admin_report.service;

import com.dasigochim.building.domain.BuildingUnit;
import com.dasigochim.building.repository.BuildingSpaceRepository;
import com.dasigochim.building.repository.BuildingUnitRepository;
import com.dasigochim.building.service.BuildingService;
import com.dasigochim.report.domain.ReportStatus;
import com.dasigochim.report.dto.ReportResponse;
import com.dasigochim.report.repository.ReportRepository;
import com.dasigochim.report.service.ReportViewService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 3D 지도. 단지 하나의 3D 장면 목록(공용공간 · 층 배치도 · 세대 타입)과
 * 그 단지에 접수된 신고 전부를 핀 좌표와 함께 돌려준다. 상태·월 필터는 화면에서 건다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSpaceMapService {
    private final BuildingService buildingService;
    private final BuildingSpaceRepository spaceRepository;
    private final BuildingUnitRepository unitRepository;
    private final ReportRepository reportRepository;
    private final ReportViewService viewService;

    public SpaceMapResponse spaceMap(Long buildingId) {
        var building = buildingService.getEntity(buildingId);
        // 장면 id → 그 장면이 보여주는 공간 이름들. 한 장면을 여러 공간이 같이 쓴다 (2층 = 피트니스·세탁실·커뮤니티).
        Map<String, Set<String>> names = new LinkedHashMap<>();
        spaceRepository.findAllByBuildingIdOrderBySortOrder(buildingId)
                .forEach(space -> names.computeIfAbsent(space.getSceneId(), key -> new LinkedHashSet<>()).add(space.getName()));
        Map<String, List<String>> unitTypesByScene = unitRepository.findAllByBuildingId(buildingId).stream()
                .collect(Collectors.groupingBy(BuildingUnit::getSceneId, LinkedHashMap::new,
                        Collectors.mapping(BuildingUnit::getUnitType, Collectors.collectingAndThen(
                                Collectors.toCollection(LinkedHashSet::new), set -> set.stream().sorted(UNIT_TYPE_ORDER).toList()))));
        unitTypesByScene.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparing(types -> types.get(0), UNIT_TYPE_ORDER)))
                .forEach(entry -> names.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>())
                        .add("세대 " + String.join("·", entry.getValue()) + " TYPE"));

        var reports = reportRepository.findAllByBuildingIdAndStatusNotOrderBySubmittedAtDesc(buildingId, ReportStatus.RECEIVING)
                .stream().map(report -> viewService.toResponse(report, false, true)).toList();
        // 장면 표에 없는 장면에 찍힌 신고도 놓치지 않게 장면 목록에 더한다.
        reports.stream().filter(report -> report.space() != null && report.space().sceneId() != null)
                .forEach(report -> names.computeIfAbsent(report.space().sceneId(), key -> new LinkedHashSet<>()));

        var scenes = names.entrySet().stream()
                .map(entry -> new SceneInfo(entry.getKey(), entry.getValue().isEmpty() ? entry.getKey() : String.join(" · ", entry.getValue())))
                .toList();
        return new SpaceMapResponse(new BuildingInfo(building.getId(), building.getName(), !unitTypesByScene.isEmpty()),
                scenes, reports);
    }

    /** "18A" < "18B" < "19" < "37" < "42" ... 숫자 우선, 같으면 글자 순. */
    private static final Comparator<String> UNIT_TYPE_ORDER = Comparator
            .comparingInt((String type) -> Integer.parseInt(type.replaceAll("\\D", "").isEmpty() ? "0" : type.replaceAll("\\D", "")))
            .thenComparing(Comparator.naturalOrder());

    public record SpaceMapResponse(BuildingInfo building, List<SceneInfo> scenes, List<ReportResponse> reports) {}
    public record BuildingInfo(Long id, String name, boolean has3d) {}
    public record SceneInfo(String sceneId, String name) {}
}
