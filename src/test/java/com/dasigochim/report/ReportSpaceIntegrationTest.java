package com.dasigochim.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dasigochim.admin_report.service.AdminSpaceMapService;
import com.dasigochim.building.domain.Building;
import com.dasigochim.building.domain.BuildingSpace;
import com.dasigochim.building.domain.BuildingUnit;
import com.dasigochim.building.repository.BuildingRepository;
import com.dasigochim.building.repository.BuildingSpaceRepository;
import com.dasigochim.building.repository.BuildingUnitRepository;
import com.dasigochim.building.repository.RegionRepository;
import com.dasigochim.building.domain.Region;
import com.dasigochim.building.service.BuildingService;
import com.dasigochim.user.service.MeService;
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
    @Autowired BuildingService buildingService;
    @Autowired RegionRepository regionRepository;
    @Autowired MeService meService;
    @Autowired AdminSpaceMapService spaceMapService;

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

    @Test
    void housingWithout3dKeepsTypedUnitAndAcceptsProfile() {
        Building plain = buildingRepository.save(new Building("3D 없는 청년주택", "PLAIN_HOUSING", "서울", "서울특별시", "용산구"));
        assertThat(buildingService.hasUnits(plain.getId())).isFalse();

        var profile = meService.updateProfile(reporter.getId(), "입주민", "010-1234-5678", plain.getId(), "", "502호");
        assertThat(profile.ho()).isEqualTo("502");
        assertThat(profile.profileCompleted()).isTrue();

        String trackingId = submit(new ReportService.LocationRequest(
                plain.getId(), "3D 없는 청년주택 502호 · 욕실 천장", null, null, "502호", null, null,
                SpaceType.UNIT, null, null, null, null, null, null, null, null));
        ReportResponse own = reportService.findByTrackingId(trackingId, reporter.getId());
        assertThat(own.room()).isEqualTo("502");
        assertThat(own.space().sceneId()).isNull();
        assertThat(own.space().pin()).isNull();
    }

    @Test
    void unitsAreListedByFloorThenHo() {
        unitRepository.save(new BuildingUnit(housing, null, "301", "3", "52", "unit_42-type"));
        unitRepository.save(new BuildingUnit(housing, null, "2001", "20", "37", "unit_37-type"));
        assertThat(buildingService.units(housing.getId()))
                .extracting(BuildingService.UnitResponse::ho)
                .containsExactly("301", "1203", "2001");
    }

    @Test
    void regionsListEveryRegionEvenWithoutHousing() {
        regionRepository.save(new Region("서울특별시", "종로구", 0));
        regionRepository.save(new Region("서울특별시", "마포구", 1));
        regionRepository.save(new Region("제주특별자치도", "제주시", 1600));
        assertThat(buildingService.regions())
                .extracting(BuildingService.RegionResponse::sido)
                .containsExactly("서울특별시", "제주특별자치도");
        assertThat(buildingService.regions().get(0).sigungus()).containsExactly("종로구", "마포구");
    }

    @Test
    void adminSpaceMapListsScenesAndPinnedReports() {
        submit(new ReportService.LocationRequest(
                housing.getId(), "1203호 욕실", null, null, "1203", null, null,
                SpaceType.UNIT, null, null, null, null, 1.0, 0.0, 1.0, "욕실"));
        submit(new ReportService.LocationRequest(
                housing.getId(), "피트니스", null, null, null, null, null,
                SpaceType.COMMON, "FITNESS", null, null, null, 2.0, 0.0, 2.0, "피트니스"));

        var map = spaceMapService.spaceMap(housing.getId());
        assertThat(map.building().has3d()).isTrue();
        assertThat(map.scenes()).extracting(AdminSpaceMapService.SceneInfo::sceneId)
                .containsExactly("neighborhood_2F", "unit_42-type");
        assertThat(map.scenes()).extracting(AdminSpaceMapService.SceneInfo::name)
                .containsExactly("피트니스", "세대 42 TYPE");
        assertThat(map.reports()).hasSize(2)
                .allSatisfy(report -> assertThat(report.space().pin()).isNotNull());
        // 관리자는 세대 내부 신고의 호수도 본다.
        assertThat(map.reports()).anySatisfy(report -> assertThat(report.room()).isEqualTo("1203"));
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
