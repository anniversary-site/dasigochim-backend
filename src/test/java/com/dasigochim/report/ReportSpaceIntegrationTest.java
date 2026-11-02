package com.dasigochim.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dasigochim.building.domain.Building;
import com.dasigochim.building.domain.BuildingSpace;
import com.dasigochim.building.domain.BuildingUnit;
import com.dasigochim.building.repository.BuildingRepository;
import com.dasigochim.building.repository.BuildingSpaceRepository;
import com.dasigochim.building.repository.BuildingUnitRepository;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.report.domain.SpaceType;
import com.dasigochim.report.dto.ReportResponse;
import com.dasigochim.report.service.ReportService;
import com.dasigochim.user.domain.User;
import com.dasigochim.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportSpaceIntegrationTest {
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired BuildingUnitRepository unitRepository;
    @Autowired BuildingSpaceRepository spaceRepository;
    @Autowired ReportService reportService;

    Building housing;
    User reporter;
    User neighbor;
    User outsider;

    @BeforeEach
    void setUp() {
        housing = buildingRepository.save(new Building("테스트 청년주택", "TEST_HOUSING", "서울", "서울특별시", "마포구"));
        Building other = buildingRepository.save(new Building("다른 청년주택", "OTHER_HOUSING", "서울", "서울특별시", "용산구"));
        unitRepository.save(new BuildingUnit(housing, null, "1203", "12", "42", "unit_42-type"));
        spaceRepository.save(new BuildingSpace(housing, "FITNESS", "피트니스", "2F", "neighborhood_2F", false, 1));
        reporter = resident("reporter", housing, "1203");
        neighbor = resident("neighbor", housing, "1204");
        outsider = resident("outsider", other, "101");
    }

    @Test
    void unitReportTakesTypeAndSceneFromUnitTableAndIsMaskedForNeighbors() {
        String trackingId = submit(new ReportService.LocationRequest(
                housing.getId(), "테스트 청년주택 1203호 욕실", null, null, "1203호", null, null,
                SpaceType.UNIT, null, null, null, "ignored-scene", 1.0, 0.2, -0.5, "욕실"));

        ReportResponse own = reportService.findByTrackingId(trackingId, reporter.getId());
        assertThat(own.room()).isEqualTo("1203");
        assertThat(own.floor()).isEqualTo("12");
        assertThat(own.space().unitType()).isEqualTo("42");
        assertThat(own.space().sceneId()).isEqualTo("unit_42-type");
        assertThat(own.space().pin().label()).isEqualTo("욕실");
        assertThat(own.space().masked()).isFalse();

        ReportResponse seenByNeighbor = reportService.findByTrackingId(trackingId, neighbor.getId());
        assertThat(seenByNeighbor.room()).isNull();
        assertThat(seenByNeighbor.floor()).isNull();
        assertThat(seenByNeighbor.locationDescription()).isEqualTo("세대 내부 · 42 TYPE · 욕실");
        assertThat(seenByNeighbor.space().masked()).isTrue();

        assertThat(reportService.feed("others", neighbor.getId())).hasSize(1)
                .first().satisfies(item -> assertThat(item.room()).isNull());
        assertThat(reportService.feed("others", reporter.getId())).isEmpty();
        assertThat(reportService.feed("mine", reporter.getId())).hasSize(1);
        assertThat(reportService.feed("others", outsider.getId())).isEmpty();
        assertThatThrownBy(() -> reportService.findByTrackingId(trackingId, outsider.getId()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> reportService.findByTrackingId(trackingId, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void commonSpaceReportStaysVisibleWithLocation() {
        String trackingId = submit(new ReportService.LocationRequest(
                housing.getId(), "테스트 청년주택 피트니스 · 러닝머신 옆", null, null, null, null, null,
                SpaceType.COMMON, "FITNESS", null, null, null, 3.0, 0.0, 2.0, "피트니스"));

        ReportResponse seenByNeighbor = reportService.findByTrackingId(trackingId, neighbor.getId());
        assertThat(seenByNeighbor.space().masked()).isFalse();
        assertThat(seenByNeighbor.space().name()).isEqualTo("피트니스");
        assertThat(seenByNeighbor.space().sceneId()).isEqualTo("neighborhood_2F");
        assertThat(seenByNeighbor.floor()).isEqualTo("2F");
        assertThat(seenByNeighbor.locationDescription()).isEqualTo("테스트 청년주택 피트니스 · 러닝머신 옆");
    }

    @Test
    void unknownUnitIsRejected() {
        Long reportId = reportService.createDraft(reporter.getId()).id();
        assertThatThrownBy(() -> reportService.updateLocation(reportId, reporter.getId(), new ReportService.LocationRequest(
                housing.getId(), "없는 호수", null, null, "9999", null, null,
                SpaceType.UNIT, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class);
    }

    private String submit(ReportService.LocationRequest location) {
        Long reportId = reportService.createDraft(reporter.getId()).id();
        reportService.updateLocation(reportId, reporter.getId(), location);
        return reportService.submit(reportId, reporter.getId()).trackingId();
    }

    private User resident(String providerId, Building building, String ho) {
        User user = new User("GOOGLE", providerId, providerId);
        user.updateResidence(building, null, ho);
        return userRepository.save(user);
    }
}
