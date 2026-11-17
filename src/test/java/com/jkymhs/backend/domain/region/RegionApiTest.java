package com.jkymhs.backend.domain.region;

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
import org.springframework.http.HttpHeaders;

import java.time.LocalDate;
import java.util.List;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static com.jkymhs.backend.domain.content.ContentFixtures.festival;
import static com.jkymhs.backend.domain.content.ContentFixtures.restaurant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시계는 ClockTestSupport 기본값 2026-11-09(월)이다.
 */
class RegionApiTest extends ClockTestSupport {

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
    @DisplayName("시·도 → 시·군·구 2단계로, 코드 순서대로, 화면 이름으로 준다")
    void tree() throws Exception {
        mockMvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regions.length()").value(3))
                .andExpect(jsonPath("$.regions[0].name").value("서울"))
                .andExpect(jsonPath("$.regions[0].children.length()").value(2))
                .andExpect(jsonPath("$.regions[0].children[0].name").value("종로구"))
                .andExpect(jsonPath("$.regions[0].children[1].name").value("중구"))
                .andExpect(jsonPath("$.regions[1].name").value("부산"))
                .andExpect(jsonPath("$.regions[2].name").value("세종"))
                .andExpect(jsonPath("$.regions[2].children.length()").value(0))
                .andExpect(jsonPath("$.regions[0].festivalCount").value(0));
    }

    @Test
    @DisplayName("개수는 삭제 안 된 관광지와 끝나지 않은 축제만 세고, 시·도는 아래 시·군·구 합이다")
    void counts() throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 9);
        contentService.upsert(List.of(
                attraction(1L, "경복궁", "11", "110"),
                attraction(2L, "덕수궁", "11", "140"),
                attraction(3L, "삭제됨", "11", "140"),
                restaurant(4L, "식당", "11", "140"),
                festival(10L, "진행 중", "11", "140", today.minusDays(1), today),
                festival(11L, "예정", "11", "110", today.plusDays(3), today.plusDays(5)),
                festival(12L, "끝남", "11", "110", today.minusDays(5), today.minusDays(1)),
                attraction(20L, "세종호수공원", "36110", "36110")
        ), contentService.loadContext());
        contentService.markDeleted(List.of(3L));

        mockMvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=86400"))
                .andExpect(jsonPath("$.regions[0].festivalCount").value(2))
                .andExpect(jsonPath("$.regions[0].attractionCount").value(2))
                .andExpect(jsonPath("$.regions[0].children[0].festivalCount").value(1))
                .andExpect(jsonPath("$.regions[0].children[0].attractionCount").value(1))
                .andExpect(jsonPath("$.regions[0].children[1].festivalCount").value(1))
                .andExpect(jsonPath("$.regions[0].children[1].attractionCount").value(1))
                .andExpect(jsonPath("$.regions[2].attractionCount").value(1));
    }
}
