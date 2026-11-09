package com.jkymhs.backend.domain.wish;

import com.jkymhs.backend.domain.content.service.TourIntro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static com.jkymhs.backend.domain.content.ContentFixtures.festival;
import static com.jkymhs.backend.domain.content.ContentFixtures.restaurant;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시계는 2026-11-09(월) 10:00 KST에서 시작해 찜할 때마다 1분씩 간다 (덕수궁·야간 개장은 같은 시각에 찜).
 *
 * 축제: 가을 축제(진행 중) · 지난 축제(끝남) · 겨울 축제(예정, 찜한 뒤 삭제됨).
 * 장소: 경복궁(월요일 휴무) · 덕수궁(10~17시, 영업 중) · 야간 개장(18~22시, 지금은 영업 전) · 식당(운영 시간 모름).
 */
class WishListApiTest extends BehaviorTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        saveContents(
                festival(1L, "가을 축제", "11", "140", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15)),
                festival(2L, "지난 축제", "11", "140", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)),
                festival(3L, "겨울 축제", "11", "110", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5)),
                attraction(11L, "경복궁", "11", "110"),
                attraction(12L, "덕수궁", "11", "140"),
                attraction(13L, "야간 개장", "11", "140"),
                restaurant(21L, "식당", "11", "140"));
        contentService.applyIntro(11L, intro("09:00~18:00", "매주 월요일"));
        contentService.applyIntro(12L, intro("10:00~17:00", null));
        contentService.applyIntro(13L, intro("18:00~22:00", null));

        token = token(saveUser(true));
        for (long id : new long[]{1, 2, 3, 11}) {
            wish(token, id);
            clock.advance(Duration.ofMinutes(1));
        }
        wish(token, 12);
        wish(token, 13);
        clock.advance(Duration.ofMinutes(1));
        wish(token, 21);
        contentService.markDeleted(List.of(3L));

        // 다른 회원의 찜은 섞이지 않는다
        wish(token(saveUser(false)), 1);
    }

    @Test
    @DisplayName("축제 탭은 최근 찜 순이고, 삭제된 콘텐츠도 deleted=true로 보이며, 첫 페이지에 탭별 개수를 준다")
    void festivalTab() throws Exception {
        mockMvc.perform(list("festival"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("겨울 축제", "지난 축제", "가을 축제")))
                .andExpect(jsonPath("$.items[*].deleted").value(contains(true, false, false)))
                .andExpect(jsonPath("$.items[*].status").value(contains("UPCOMING", "ENDED", "ONGOING")))
                .andExpect(jsonPath("$.items[0].liked").value(true))
                .andExpect(jsonPath("$.items[0].kind").value("festival"))
                .andExpect(jsonPath("$.items[2].wishedAt").value("2026-11-09T10:00:00"))
                .andExpect(jsonPath("$.counts.festival").value(3))
                .andExpect(jsonPath("$.counts.place").value(4))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("장소 탭은 축제가 아닌 모든 종류를 주고, 같은 시각에 찜하면 나중에 찜한 것이 먼저다")
    void placeTab() throws Exception {
        mockMvc.perform(list("place"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("식당", "야간 개장", "덕수궁", "경복궁")))
                .andExpect(jsonPath("$.items[*].openStatus").value(contains(null, "closed", "open", "closed")))
                .andExpect(jsonPath("$.items[3].restDays").value(contains("MON")))
                .andExpect(jsonPath("$.counts.festival").value(3))
                .andExpect(jsonPath("$.counts.place").value(4));
    }

    @Test
    @DisplayName("끝난 축제 숨기기, 오늘 영업 안 하는 곳 숨기기를 켜면 빠지지만 탭 개수는 그대로다")
    void hideToggles() throws Exception {
        mockMvc.perform(list("festival").param("hideEnded", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("겨울 축제", "가을 축제")))
                .andExpect(jsonPath("$.counts.festival").value(3));
        mockMvc.perform(list("place").param("hideClosedToday", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("식당", "덕수궁")))
                .andExpect(jsonPath("$.counts.place").value(4));

        // 저녁 7시가 되면 야간 개장은 영업 중, 덕수궁은 닫힘
        clock.setNow(LocalDate.of(2026, 11, 9).atTime(19, 0));
        mockMvc.perform(list("place").param("hideClosedToday", "true"))
                .andExpect(jsonPath("$.items[*].name").value(contains("식당", "야간 개장")));
    }

    @Test
    @DisplayName("커서로 이어 읽으면 빠짐·중복 없이 이어지고, 다음 페이지에는 개수를 주지 않는다")
    void cursor() throws Exception {
        JsonNode first = read(list("place").param("size", "2"));
        mockMvc.perform(list("place").param("size", "2"))
                .andExpect(jsonPath("$.items[*].name").value(contains("식당", "야간 개장")))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(list("place").param("size", "2").param("cursor", first.path("nextCursor").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("덕수궁", "경복궁")))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.counts").value(nullValue()));
    }

    @Test
    @DisplayName("풀 수 없는 커서나 다른 탭의 커서는 400 INVALID_CURSOR, 탭이 없거나 크기가 범위를 벗어나면 400 INVALID_INPUT이다")
    void invalidRequests() throws Exception {
        JsonNode first = read(list("place").param("size", "1"));

        mockMvc.perform(list("festival").param("cursor", first.path("nextCursor").asString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
                .andExpect(jsonPath("$.message").value("목록을 처음부터 다시 불러옵니다."));
        mockMvc.perform(list("place").param("cursor", "garbage"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mockMvc.perform(auth(get("/api/wishes"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("잘못된 필터 값입니다."));
        mockMvc.perform(list("place").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private MockHttpServletRequestBuilder list(String tab) {
        return auth(get("/api/wishes"), token).param("tab", tab);
    }

    private void wish(String accessToken, long contentId) throws Exception {
        mockMvc.perform(auth(post("/api/wishes/" + contentId), accessToken)).andExpect(status().isOk());
    }

    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JSON.readTree(body);
    }

    private static TourIntro intro(String useTime, String restDate) {
        return new TourIntro(useTime, restDate, null, null, null, null, null, null, null, null);
    }
}
