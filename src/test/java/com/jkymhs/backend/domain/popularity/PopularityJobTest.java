package com.jkymhs.backend.domain.popularity;

import com.jkymhs.backend.domain.content.entity.BatchJobName;
import com.jkymhs.backend.domain.content.entity.BatchJobStatus;
import com.jkymhs.backend.domain.content.service.BatchJobLogService;
import com.jkymhs.backend.domain.content.service.ContentRedisKeys;
import com.jkymhs.backend.domain.popularity.service.PopularityJob;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.wish.BehaviorTestSupport;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 원점수 = 조회 × 1 + 찜 × 3 + 일정 담기 × 5 (최근 30일), 점수 = round(1000 × ln(1+raw) / ln(1+maxRaw)).
 * 경복궁: 조회 3 → 3점 · 덕수궁: 찜 1 + 담기 1 → 8점(최고) · 박물관: 40일 전 조회만 → 0점.
 */
class PopularityJobTest extends BehaviorTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private PopularityJob popularityJob;

    @Autowired
    private BatchJobLogService batchJobLogService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() throws Exception {
        saveContents(attraction(1L, "경복궁", "11", "110"), attraction(2L, "덕수궁", "11", "140"),
                attraction(3L, "박물관", "11", "140"));
        contentService.updatePopularityScores(Map.of(3L, 500));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(auth(get("/api/contents/1"), token(saveUser(false)))).andExpect(status().isOk());
        }
        User user = saveUser(false);
        mockMvc.perform(auth(post("/api/wishes/2"), token(user))).andExpect(status().isOk());
        eventPublisher.publishEvent(new BehaviorSignalEvent(user.getId(), 2L, BehaviorSignalType.SCHEDULE_SAVE,
                null, null, LocalDateTime.of(2026, 11, 9, 9, 0)));
        jdbcTemplate.update("INSERT INTO content_views (user_id, content_id, view_date, created_at) VALUES (?, ?, ?, ?)",
                user.getId(), 3L, LocalDate.of(2026, 9, 30), LocalDateTime.of(2026, 9, 30, 12, 0));
    }

    @Test
    @DisplayName("최근 30일 행동으로 0~1000점을 매기고, 버전을 1 올리고, 실행 기록을 남긴다")
    void normalizesAndBumpsVersion() {
        int updated = popularityJob.run();

        assertThat(updated).isEqualTo(2);
        assertThat(popularityOf(2L)).isEqualTo(1000);
        assertThat(popularityOf(1L)).isEqualTo((int) Math.round(1000 * Math.log(4) / Math.log(9)));
        assertThat(popularityOf(3L)).isZero();
        assertThat(redisTemplate.opsForValue().get(ContentRedisKeys.popularityVersion())).isEqualTo("1");
        assertThat(batchJobLogService.findRecent(BatchJobName.POPULARITY)).singleElement().satisfies(log -> {
            assertThat(log.getStatus()).isEqualTo(BatchJobStatus.SUCCESS);
            assertThat(log.getProcessedCount()).isEqualTo(2);
            assertThat(log.getFinishedAt()).isNotNull();
        });

        popularityJob.run();
        assertThat(redisTemplate.opsForValue().get(ContentRedisKeys.popularityVersion())).isEqualTo("2");
    }

    @Test
    @DisplayName("배치 전에 받은 인기순 커서로 배치 뒤 다음 페이지를 읽으면 409다")
    void popularCursorAfterBatchConflicts() throws Exception {
        String body = mockMvc.perform(get("/api/contents").param("tab", "place").param("sort", "popular")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String cursor = JSON.readTree(body).path("nextCursor").asString();

        popularityJob.run();

        mockMvc.perform(get("/api/contents").param("tab", "place").param("sort", "popular").param("size", "1")
                        .param("cursor", cursor))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CURSOR_VERSION_MISMATCH"))
                .andExpect(jsonPath("$.message").value("목록이 갱신되었습니다. 처음부터 다시 불러옵니다."));
    }

    @Test
    @DisplayName("30일이 지나면 그 행동은 빠진다 (모두 지나면 모두 0점)")
    void windowExpires() {
        clock.setNow(LocalDateTime.of(2026, 12, 10, 10, 0));

        assertThat(popularityJob.run()).isZero();

        assertThat(popularityOf(1L)).isZero();
        assertThat(popularityOf(2L)).isZero();
    }
}
