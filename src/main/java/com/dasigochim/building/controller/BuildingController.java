package com.dasigochim.building.controller;

import com.dasigochim.building.service.BuildingService;
import com.dasigochim.common.response.ApiResponse;
import com.dasigochim.report.dto.ReportResponse;
import com.dasigochim.report.service.ReportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/buildings")
@RequiredArgsConstructor
public class BuildingController {
    private final BuildingService buildingService;
    private final ReportService reportService;

    @GetMapping
    ApiResponse<List<BuildingService.BuildingResponse>> all(@RequestParam(required = false) String query) {
        return ApiResponse.ok(buildingService.findAll(query));
    }

    @GetMapping("/{id}")
    ApiResponse<BuildingService.BuildingResponse> one(@PathVariable Long id) {
        return ApiResponse.ok(buildingService.findOne(id));
    }

    @GetMapping("/{id}/reports")
    ApiResponse<List<ReportResponse>> reports(@PathVariable Long id) {
        return ApiResponse.ok(reportService.byBuilding(id));
    }
}
