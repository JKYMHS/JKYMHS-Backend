package com.jkymhs.backend.domain.region;

import com.jkymhs.backend.domain.content.ContentFixtures;
import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.domain.region.service.RegionSource;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegionServiceTest extends IntegrationTestSupport {

    @Autowired
    private RegionService regionService;

    private List<RegionRef> regions;

    @BeforeEach
    void setUp() {
        regionService.upsertAll(ContentFixtures.REGIONS);
        regions = regionService.getAllRegions();
    }

    @Test
    @DisplayName("시·도는 짧은 이름, 시·군·구는 fullName \"서울 중구\"로 준다")
    void regionRef() {
        RegionRef jung = regionService.getRegion(ContentFixtures.regionId(regions, "서울 중구"));

        assertThat(jung.name()).isEqualTo("중구");
        assertThat(jung.parentName()).isEqualTo("서울");
        assertThat(jung.depth()).isEqualTo(2);
        assertThat(regionService.getRegion(ContentFixtures.regionId(regions, "부산")).fullName()).isEqualTo("부산");
    }

    @Test
    @DisplayName("다시 적재하면 코드가 같은 지역은 id를 유지한 채 이름만 바뀐다")
    void upsertIsIdempotent() {
        Long seoulId = ContentFixtures.regionId(regions, "서울");

        regionService.upsertAll(List.of(new RegionSource("11", "", "서울특별시"), new RegionSource("11", "140", "중구")));
        regionService.upsertAll(ContentFixtures.REGIONS);

        List<RegionRef> again = regionService.getAllRegions();
        assertThat(again).hasSize(ContentFixtures.REGIONS.size());
        assertThat(ContentFixtures.regionId(again, "서울")).isEqualTo(seoulId);
        assertThat(regionService.childrenOf(seoulId)).extracting(RegionRef::name).containsExactly("종로구", "중구");
    }

    @Test
    @DisplayName("없는 지역이나 다른 시·도의 시·군·구는 400 \"잘못된 필터 값입니다.\"이다")
    void invalidRegion() {
        Long seoul = ContentFixtures.regionId(regions, "서울");
        Long haeundae = ContentFixtures.regionId(regions, "부산 해운대구");

        assertThatThrownBy(() -> regionService.getRegion(999_999L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    assertThat(e.getMessage()).isEqualTo("잘못된 필터 값입니다.");
                });
        assertThatThrownBy(() -> regionService.validateDistricts(seoul, List.of(haeundae)))
                .isInstanceOf(CustomException.class);
        regionService.validateDistricts(seoul, List.of(ContentFixtures.regionId(regions, "서울 중구")));
    }
}
