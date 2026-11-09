package com.jkymhs.backend.domain.content.controller;

import com.jkymhs.backend.domain.category.service.CategorySeeder;
import com.jkymhs.backend.domain.content.ContentFixtures;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.TourClassificationService;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.support.ClockTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static com.jkymhs.backend.domain.content.ContentFixtures.festival;
import static com.jkymhs.backend.domain.content.ContentFixtures.restaurant;
import static com.jkymhs.backend.domain.content.ExploreFixtures.at;
import static com.jkymhs.backend.domain.content.ExploreFixtures.BASE_LAT;
import static com.jkymhs.backend.domain.content.ExploreFixtures.BASE_LNG;
import static com.jkymhs.backend.domain.content.ExploreFixtures.northOfBase;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기준 콘텐츠(1번)는 서울시청 좌표다. 시계 기본값은 2026-11-09(월)이다.
 */
class NearbyPicksApiTest extends ClockTestSupport {

    @Autowired
    private RegionService regionService;

    @Autowired
    private TourClassificationService tourClassificationService;

    @Autowired
    private CategorySeeder categorySeeder;

    @Autowired
    private ContentService contentService;

    @BeforeEach
    void setUp() {
        regionService.upsertAll(ContentFixtures.REGIONS);
        tourClassificationService.upsertAll(ContentFixtures.CLASSIFICATIONS);
        categorySeeder.seed();
    }

    @Test
    @DisplayName("1km 안의 관광지·끝나지 않은 축제를 가까운 순으로 4곳까지 주고, 자기 자신·음식점·끝난 축제·1km 밖은 뺀다")
    void picks() throws Exception {
        contentService.upsert(List.of(
                at(attraction(1L, "기준", "11", "140"), BASE_LAT, BASE_LNG),
                northOfBase(attraction(2L, "300m", "11", "140"), 300),
                northOfBase(festival(3L, "500m 축제", "11", "140", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20)), 500),
                northOfBase(attraction(4L, "700m", "11", "140"), 700),
                northOfBase(attraction(5L, "900m", "11", "140"), 900),
                northOfBase(attraction(6L, "950m", "11", "140"), 950),
                northOfBase(attraction(7L, "1500m", "11", "140"), 1500),
                northOfBase(festival(8L, "끝난 축제", "11", "140", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)), 100),
                northOfBase(restaurant(9L, "식당", "11", "140"), 50)
        ), contentService.loadContext());
        contentService.updatePopularityScores(Map.of(6L, 1000));

        mockMvc.perform(get("/api/contents/1/nearby-picks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("300m", "500m 축제", "700m", "900m")))
                .andExpect(jsonPath("$.items[0].kind").value("attraction"))
                .andExpect(jsonPath("$.items[1].kind").value("festival"))
                .andExpect(jsonPath("$.items[0].category").value("궁"))
                .andExpect(jsonPath("$.items[0].distanceMeters").value(allOf(greaterThanOrEqualTo(295), lessThanOrEqualTo(305))))
                // 300m ÷ 67m/분 = 4.5분 → 올림 5분
                .andExpect(jsonPath("$.items[0].walkMinutes").value(5));
    }

    @Test
    @DisplayName("기준 콘텐츠가 삭제됐거나 없으면 404다")
    void notFound() throws Exception {
        contentService.upsert(List.of(attraction(1L, "기준", "11", "140")), contentService.loadContext());
        contentService.markDeleted(List.of(1L));

        mockMvc.perform(get("/api/contents/1/nearby-picks"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않거나 삭제된 장소입니다."));
        mockMvc.perform(get("/api/contents/12345/nearby-picks"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
