package com.jkymhs.backend.domain.content.controller;

import com.jkymhs.backend.domain.category.service.CategorySeeder;
import com.jkymhs.backend.domain.content.ContentFixtures;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.TourClassificationService;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static com.jkymhs.backend.domain.content.ContentFixtures.content;
import static com.jkymhs.backend.domain.content.ContentFixtures.restaurant;
import static com.jkymhs.backend.domain.content.ExploreFixtures.BASE_LAT;
import static com.jkymhs.backend.domain.content.ExploreFixtures.BASE_LNG;
import static com.jkymhs.backend.domain.content.ExploreFixtures.METERS_PER_LAT_DEGREE;
import static com.jkymhs.backend.domain.content.ExploreFixtures.northOfBase;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 음식점: 500m(100점) · 900m(900점) · 1500m. 숙박: 2500m. 모두 서울시청에서 정북쪽이다.
 */
class ContentNearbyApiTest extends IntegrationTestSupport {

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
        contentService.upsert(List.of(
                northOfBase(restaurant(1L, "500m 식당", "11", "140"), 500),
                northOfBase(restaurant(2L, "900m 식당", "11", "140"), 900),
                northOfBase(restaurant(3L, "1500m 식당", "11", "140"), 1500),
                northOfBase(content(4L, ContentType.LODGING, "2500m 숙소", "11", "140", null, null, null, null, null), 2500)
        ), contentService.loadContext());
        contentService.updatePopularityScores(Map.of(1L, 100, 2L, 900));
    }

    @Test
    @DisplayName("음식점 기본 반경은 1000m이고 인기 점수 높은 순으로 주며, 전체 개수를 같이 준다")
    void restaurantDefault() throws Exception {
        mockMvc.perform(get("/api/contents/nearby").param("type", "RESTAURANT")
                        .param("lat", String.valueOf(BASE_LAT)).param("lng", String.valueOf(BASE_LNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("900m 식당", "500m 식당")))
                .andExpect(jsonPath("$.items[0].kind").value("restaurant"))
                .andExpect(jsonPath("$.items[0].category").value("한식"))
                .andExpect(jsonPath("$.items[0].address").value("주소 900m 식당"))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.radiusMeters").value(1000));

        mockMvc.perform(get("/api/contents/nearby").param("type", "RESTAURANT").param("size", "1")
                        .param("lat", String.valueOf(BASE_LAT)).param("lng", String.valueOf(BASE_LNG)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("숙박 기본 반경은 3000m다")
    void lodgingDefault() throws Exception {
        mockMvc.perform(get("/api/contents/nearby").param("type", "LODGING")
                        .param("lat", String.valueOf(BASE_LAT)).param("lng", String.valueOf(BASE_LNG)))
                .andExpect(jsonPath("$.items[*].name").value(contains("2500m 숙소")))
                .andExpect(jsonPath("$.radiusMeters").value(3000));
    }

    @Test
    @DisplayName("반경 안에 하나도 없으면 반경을 두 배로 한 번 더 찾고, 그 반경을 알려준다")
    void doubling() throws Exception {
        // 서울시청 북쪽 2700m: 식당까지 거리는 2200m·1800m·1200m라 1000m 안에는 없고 2000m 안에 둘이 있다
        double lat = BASE_LAT + 2700 / METERS_PER_LAT_DEGREE;
        mockMvc.perform(get("/api/contents/nearby").param("type", "RESTAURANT")
                        .param("lat", String.valueOf(lat)).param("lng", String.valueOf(BASE_LNG)))
                .andExpect(jsonPath("$.items[*].name").value(contains("900m 식당", "1500m 식당")))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.radiusMeters").value(2000));
    }

    @Test
    @DisplayName("두 배로 늘린 반경도 5000m를 넘지 않고, 그래도 없으면 빈 목록이다")
    void maxRadius() throws Exception {
        double farLat = BASE_LAT + 20_000 / METERS_PER_LAT_DEGREE;
        mockMvc.perform(get("/api/contents/nearby").param("type", "RESTAURANT").param("radius", "4000")
                        .param("lat", String.valueOf(farLat)).param("lng", String.valueOf(BASE_LNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(empty()))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.radiusMeters").value(5000));
    }

    @Test
    @DisplayName("좌표가 범위를 벗어나거나 없거나, 반경이 5000m를 넘으면 400이다")
    void invalid() throws Exception {
        mockMvc.perform(get("/api/contents/nearby").param("type", "RESTAURANT").param("lat", "91").param("lng", "127"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("좌표 값이 올바르지 않습니다."));
        mockMvc.perform(get("/api/contents/nearby").param("type", "RESTAURANT").param("lng", "127"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("좌표 값이 올바르지 않습니다."));
        mockMvc.perform(get("/api/contents/nearby").param("type", "RESTAURANT").param("lat", "37.5").param("lng", "127")
                        .param("radius", "6000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/contents/nearby").param("type", "ATTRACTION").param("lat", "37.5").param("lng", "127"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
