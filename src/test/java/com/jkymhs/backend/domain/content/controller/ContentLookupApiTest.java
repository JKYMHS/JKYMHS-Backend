package com.jkymhs.backend.domain.content.controller;

import com.jkymhs.backend.domain.category.service.CategorySeeder;
import com.jkymhs.backend.domain.content.ContentFixtures;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.TourClassificationService;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static com.jkymhs.backend.domain.content.ContentFixtures.festival;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContentLookupApiTest extends IntegrationTestSupport {

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
                attraction(1L, "경복궁", "11", "110"),
                attraction(2L, "삭제된 곳", "11", "140"),
                festival(3L, "가을 축제", "11", "140", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15))
        ), contentService.loadContext());
        contentService.markDeleted(List.of(2L));
    }

    @Test
    @DisplayName("보낸 순서대로 볼 수 있는 콘텐츠만 주고, 삭제됐거나 없는 id는 missingIds로 준다 (중복 id는 한 번)")
    void lookup() throws Exception {
        mockMvc.perform(get("/api/contents/lookup").param("ids", "3,1,999,2,1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("가을 축제", "경복궁")))
                .andExpect(jsonPath("$.items[0].kind").value("festival"))
                .andExpect(jsonPath("$.items[1].kind").value("attraction"))
                .andExpect(jsonPath("$.missingIds").value(contains(999, 2)));

        mockMvc.perform(get("/api/contents/lookup").param("ids", "1").param("ids", "3"))
                .andExpect(jsonPath("$.items[*].name").value(contains("경복궁", "가을 축제")));
    }

    @Test
    @DisplayName("id가 없거나 50개를 넘거나 숫자가 아니면 400이다")
    void invalid() throws Exception {
        mockMvc.perform(get("/api/contents/lookup"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("조회할 id를 1개 이상 보내주세요."));
        String ids = LongStream.rangeClosed(1, 51).mapToObj(Long::toString).collect(Collectors.joining(","));
        mockMvc.perform(get("/api/contents/lookup").param("ids", ids))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("한 번에 50개까지 조회할 수 있습니다."));
        mockMvc.perform(get("/api/contents/lookup").param("ids", "1,abc"))
                .andExpect(status().isBadRequest());
    }
}
