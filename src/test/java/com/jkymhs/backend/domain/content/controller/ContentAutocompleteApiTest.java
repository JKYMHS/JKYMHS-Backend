package com.jkymhs.backend.domain.content.controller;

import com.jkymhs.backend.domain.category.service.CategorySeeder;
import com.jkymhs.backend.domain.content.ContentFixtures;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSource;
import com.jkymhs.backend.domain.content.service.TourClassificationService;
import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.support.ClockTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static com.jkymhs.backend.domain.content.ContentFixtures.festival;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시계 기본값은 2026-11-09(월) 10:00 KST다.
 */
class ContentAutocompleteApiTest extends ClockTestSupport {

    @Autowired
    private RegionService regionService;

    @Autowired
    private TourClassificationService tourClassificationService;

    @Autowired
    private CategorySeeder categorySeeder;

    @Autowired
    private ContentService contentService;

    private List<RegionRef> regions;

    @BeforeEach
    void setUp() {
        regionService.upsertAll(ContentFixtures.REGIONS);
        tourClassificationService.upsertAll(ContentFixtures.CLASSIFICATIONS);
        categorySeeder.seed();
        regions = regionService.getAllRegions();
    }

    @Test
    @DisplayName("장소·축제·지역으로 나눠 주고, 이름이 검색어로 시작하는 것이 먼저이며 끝난 축제는 뺀다")
    void grouping() throws Exception {
        load(List.of(
                attraction(1L, "경복궁", "11", "110"),
                attraction(2L, "신경복 공원", "26", "350"),
                festival(3L, "경복 축제", "11", "140", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20)),
                festival(4L, "경복 지난 축제", "11", "140", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2))
        ), Map.of(1L, 100, 2L, 900, 3L, 50));

        mockMvc.perform(get("/api/contents/autocomplete").param("q", "경복"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[*].name").value(contains("경복궁", "신경복 공원")))
                .andExpect(jsonPath("$.places[0].regionName").value("서울"))
                .andExpect(jsonPath("$.places[0].districtName").value("종로구"))
                .andExpect(jsonPath("$.places[0].category").value("궁"))
                .andExpect(jsonPath("$.festivals[*].name").value(contains("경복 축제")))
                .andExpect(jsonPath("$.festivals[0].status").value("ONGOING"))
                .andExpect(jsonPath("$.festivals[0].startDate").value("2026-11-01"))
                .andExpect(jsonPath("$.regions").value(empty()));

        mockMvc.perform(get("/api/contents/autocomplete").param("q", "경복").param("tab", "festival"))
                .andExpect(jsonPath("$.places").value(empty()))
                .andExpect(jsonPath("$.festivals.length()").value(1));
    }

    @Test
    @DisplayName("regionId를 주면 그 지역 결과가 앞부분 일치·인기 점수보다 먼저다")
    void regionFirst() throws Exception {
        load(List.of(
                attraction(1L, "경복궁", "11", "110"),
                attraction(2L, "신경복 공원", "26", "350")
        ), Map.of(1L, 900, 2L, 10));
        Long haeundae = ContentFixtures.regionId(regions, "부산 해운대구");
        Long busan = ContentFixtures.regionId(regions, "부산");

        mockMvc.perform(get("/api/contents/autocomplete").param("q", "경복").param("regionId", haeundae.toString()))
                .andExpect(jsonPath("$.places[*].name").value(contains("신경복 공원", "경복궁")));
        mockMvc.perform(get("/api/contents/autocomplete").param("q", "경복").param("regionId", busan.toString()))
                .andExpect(jsonPath("$.places[*].name").value(contains("신경복 공원", "경복궁")));
    }

    @Test
    @DisplayName("합계는 최대 10개이고 지역은 이름 앞부분이 맞는 것 2개까지다")
    void limit() throws Exception {
        List<ContentSource> sources = new ArrayList<>();
        Map<Long, Integer> scores = new HashMap<>();
        for (int i = 1; i <= 12; i++) {
            sources.add(attraction(i, "해운대 장소 " + i, "26", "350"));
            scores.put((long) i, i);
        }
        load(sources, scores);

        mockMvc.perform(get("/api/contents/autocomplete").param("q", "해운대"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regions.length()").value(1))
                .andExpect(jsonPath("$.regions[0].name").value("해운대구"))
                .andExpect(jsonPath("$.regions[0].parentName").value("부산"))
                .andExpect(jsonPath("$.places.length()").value(9))
                .andExpect(jsonPath("$.places[0].name").value("해운대 장소 12"));

        mockMvc.perform(get("/api/contents/autocomplete").param("q", "서울"))
                .andExpect(jsonPath("$.regions[*].name").value(contains("서울", "종로구")));
    }

    @Test
    @DisplayName("검색어가 특수문자를 지우고 2자 미만이면 400이다")
    void tooShort() throws Exception {
        mockMvc.perform(get("/api/contents/autocomplete").param("q", "경!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("검색어는 2자 이상 입력해주세요."));
        mockMvc.perform(get("/api/contents/autocomplete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("검색어는 2자 이상 입력해주세요."));
    }

    private void load(List<ContentSource> sources, Map<Long, Integer> scores) {
        contentService.upsert(sources, contentService.loadContext());
        contentService.updatePopularityScores(scores);
    }
}
