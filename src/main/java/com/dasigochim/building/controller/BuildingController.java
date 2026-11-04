package com.dasigochim.building.controller;

import com.dasigochim.building.service.BuildingService;
import com.dasigochim.common.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/buildings")
@RequiredArgsConstructor
public class BuildingController {
    private final BuildingService buildingService;

    @GetMapping
    ApiResponse<List<BuildingService.BuildingResponse>> all(@RequestParam(required = false) String query,
                                                           @RequestParam(required = false) String sido,
                                                           @RequestParam(required = false) String sigungu) {
        return ApiResponse.ok(buildingService.findAll(query, sido, sigungu));
    }

    @GetMapping("/regions")
    ApiResponse<List<BuildingService.RegionResponse>> regions() {
        return ApiResponse.ok(buildingService.regions());
    }

    @GetMapping("/{id}")
    ApiResponse<BuildingService.BuildingResponse> one(@PathVariable Long id) {
        return ApiResponse.ok(buildingService.findOne(id));
    }

    @GetMapping("/{id}/spaces")
    ApiResponse<List<BuildingService.SpaceResponse>> spaces(@PathVariable Long id) {
        return ApiResponse.ok(buildingService.spaces(id));
    }

    @GetMapping("/{id}/units")
    ApiResponse<List<BuildingService.UnitResponse>> units(@PathVariable Long id) {
        return ApiResponse.ok(buildingService.units(id));
    }

    @GetMapping("/{id}/units/lookup")
    ApiResponse<BuildingService.UnitResponse> unit(@PathVariable Long id,
                                                  @RequestParam(required = false) String dong,
                                                  @RequestParam String ho) {
        return ApiResponse.ok(buildingService.lookupUnit(id, dong, ho));
    }
}
