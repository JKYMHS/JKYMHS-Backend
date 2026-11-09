package com.jkymhs.backend.domain.content.controller;

import com.jkymhs.backend.domain.category.service.CategorySeeder;
import com.jkymhs.backend.domain.content.ContentFixtures;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.TourClassificationService;
import com.jkymhs.backend.domain.content.service.TourIntro;
import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.support.ClockTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static com.jkymhs.backend.domain.content.ContentFixtures.content;
import static com.jkymhs.backend.domain.content.ContentFixtures.festival;
import static com.jkymhs.backend.domain.content.ContentFixtures.restaurant;
import static com.jkymhs.backend.domain.content.ExploreFixtures.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시계 기본값은 2026-11-09(월) 10:00 KST다.
 *
 * 축제: 가을 축제(11-01~11-15 진행 중, 서울 중구, 500점) · 겨울 축제(12-01~12-05 예정, 서울 종로구, 900점) ·
 * 지난 축제(10-01~10-05 끝남) · 토요일 축제(11-14 하루, 부산 해운대구, 850점) · 다음주 축제(11-20~11-22, 0점).
 * 관광지: 경복궁(종로구, 700점, 09~18시, 화요일 휴무) · 덕수궁(중구, 700점, 10~17시) · 박물관(중구, 300점, 10~21시, 연중무휴).
 * 음식점 1곳은 어느 탭에도 나오지 않는다.
 */
class ContentListApiTest extends ClockTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private RegionService regionService;

    @Autowired
    private TourClassificationService tourClassificationService;

    @Autowired
    private CategorySeeder categorySeeder;

    @Autowired
    private ContentService contentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<RegionRef> regions;

    @BeforeEach
    void setUp() {
        regionService.upsertAll(ContentFixtures.REGIONS);
        tourClassificationService.upsertAll(ContentFixtures.CLASSIFICATIONS);
        categorySeeder.seed();
        regions = regionService.getAllRegions();
        contentService.upsert(List.of(
                festival(1L, "가을 축제", "11", "140", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15)),
                festival(2L, "겨울 축제", "11", "110", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5)),
                festival(3L, "지난 축제", "11", "140", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)),
                festival(4L, "토요일 축제", "26", "350", LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 14)),
                festival(5L, "다음주 축제", "11", "140", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22)),
                at(attraction(11L, "경복궁", "11", "110"), 37.5796, 126.9770),
                at(attraction(12L, "덕수궁", "11", "140"), 37.5658, 126.9751),
                at(content(13L, ContentType.ATTRACTION, "박물관", "11", "140",
                        "VE", "VE07", "VE070100", null, null), 35.1000, 129.0000),
                restaurant(21L, "식당", "11", "140")
        ), contentService.loadContext());
        contentService.updatePopularityScores(Map.of(1L, 500, 2L, 900, 4L, 850, 11L, 700, 12L, 700, 13L, 300));
        contentService.applyIntro(1L, intro(null, null, "18:00~22:00"));
        contentService.applyIntro(11L, intro("09:00~18:00", "매주 화요일", null));
        contentService.applyIntro(12L, intro("10:00~17:00", null, null));
        contentService.applyIntro(13L, intro("10:00~21:00", "연중무휴", null));
    }

    @Test
    @DisplayName("축제 탭 기본값은 진행 중·예정 축제를 시작일 빠른 순으로 주고, 첫 페이지에 탭별 개수와 기준일을 준다")
    void festivalDefaults() throws Exception {
        mockMvc.perform(get("/api/contents").param("tab", "festival"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("가을 축제", "토요일 축제", "다음주 축제", "겨울 축제")))
                .andExpect(jsonPath("$.items[0].kind").value("festival"))
                .andExpect(jsonPath("$.items[0].status").value("ONGOING"))
                .andExpect(jsonPath("$.items[0].startDate").value("2026-11-01"))
                .andExpect(jsonPath("$.items[0].endDate").value("2026-11-15"))
                .andExpect(jsonPath("$.items[0].regionName").value("서울"))
                .andExpect(jsonPath("$.items[0].districtName").value("중구"))
                .andExpect(jsonPath("$.items[0].io").value("OUTDOOR"))
                .andExpect(jsonPath("$.items[0].category").value("문화관광축제"))
                .andExpect(jsonPath("$.items[0].popularity").value(500))
                .andExpect(jsonPath("$.items[0].liked").value(false))
                .andExpect(jsonPath("$.items[0].openStatus").value(nullValue()))
                .andExpect(jsonPath("$.items[1].status").value("UPCOMING"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.counts.festival").value(4))
                .andExpect(jsonPath("$.counts.place").value(3))
                .andExpect(jsonPath("$.asOf").value("2026-11-09"));
    }

    @Test
    @DisplayName("관광지 탭 기본값은 인기순이고 점수가 같으면 이름순이며, 영업 상태·운영 시각·쉬는 요일을 준다")
    void placeDefaults() throws Exception {
        mockMvc.perform(get("/api/contents").param("tab", "place"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("경복궁", "덕수궁", "박물관")))
                .andExpect(jsonPath("$.items[0].kind").value("attraction"))
                .andExpect(jsonPath("$.items[0].status").value(nullValue()))
                .andExpect(jsonPath("$.items[0].openStatus").value("open"))
                .andExpect(jsonPath("$.items[0].openTime").value("09:00"))
                .andExpect(jsonPath("$.items[0].closeTime").value("18:00"))
                .andExpect(jsonPath("$.items[0].restDays").value(contains("TUE")))
                .andExpect(jsonPath("$.items[0].lat").value(37.5796))
                .andExpect(jsonPath("$.items[0].lng").value(126.977))
                .andExpect(jsonPath("$.items[0].category").value("궁"))
                .andExpect(jsonPath("$.items[1].restDays").value(nullValue()))
                .andExpect(jsonPath("$.items[2].restDays").value(empty()));
    }

    @Test
    @DisplayName("최신순은 커서로 이어 읽으면 빠짐·중복 없이 이어지고, 다음 페이지에는 개수를 주지 않는다")
    void latestCursor() throws Exception {
        JsonNode first = read(get("/api/contents").param("tab", "festival").param("size", "2"));
        assertThat(names(first)).containsExactly("가을 축제", "토요일 축제");
        assertThat(first.path("hasNext").asBoolean()).isTrue();

        mockMvc.perform(get("/api/contents").param("tab", "festival").param("size", "2")
                        .param("cursor", first.path("nextCursor").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("다음주 축제", "겨울 축제")))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.counts").value(nullValue()));
    }

    @Test
    @DisplayName("인기순은 점수가 같은 항목 사이에서 끊겨도 커서로 이어진다")
    void popularCursorWithTie() throws Exception {
        JsonNode first = read(get("/api/contents").param("tab", "place").param("size", "1"));
        JsonNode second = read(get("/api/contents").param("tab", "place").param("size", "1")
                .param("cursor", first.path("nextCursor").asString()));
        JsonNode third = read(get("/api/contents").param("tab", "place").param("size", "1")
                .param("cursor", second.path("nextCursor").asString()));

        assertThat(names(first)).containsExactly("경복궁");
        assertThat(names(second)).containsExactly("덕수궁");
        assertThat(names(third)).containsExactly("박물관");
        assertThat(third.path("hasNext").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("관광지 이름순과 축제 인기순 정렬")
    void otherSorts() throws Exception {
        mockMvc.perform(get("/api/contents").param("tab", "place").param("sort", "name").param("size", "2"))
                .andExpect(jsonPath("$.items[*].name").value(contains("경복궁", "덕수궁")));
        JsonNode first = read(get("/api/contents").param("tab", "place").param("sort", "name").param("size", "2"));
        mockMvc.perform(get("/api/contents").param("tab", "place").param("sort", "name")
                        .param("cursor", first.path("nextCursor").asString()))
                .andExpect(jsonPath("$.items[*].name").value(contains("박물관")));

        mockMvc.perform(get("/api/contents").param("tab", "festival").param("sort", "popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("겨울 축제", "토요일 축제", "가을 축제", "다음주 축제")));
    }

    @Test
    @DisplayName("status는 쉼표로 여러 개를 받고, 끝난 축제도 고르면 보인다")
    void statusCsv() throws Exception {
        mockMvc.perform(get("/api/contents").param("tab", "festival").param("status", "ENDED"))
                .andExpect(jsonPath("$.items[*].name").value(contains("지난 축제")))
                .andExpect(jsonPath("$.items[0].status").value("ENDED"));
        mockMvc.perform(get("/api/contents").param("tab", "festival").param("status", "ONGOING,ENDED"))
                .andExpect(jsonPath("$.items[*].name").value(contains("지난 축제", "가을 축제")));
    }

    @Test
    @DisplayName("ongoing 칩은 status보다 우선하고, popular800 칩은 800점 이상만 준다")
    void ongoingAndPopularChips() throws Exception {
        mockMvc.perform(get("/api/contents").param("tab", "festival").param("chip", "ongoing").param("status", "UPCOMING"))
                .andExpect(jsonPath("$.items[*].name").value(contains("가을 축제")));
        mockMvc.perform(get("/api/contents").param("tab", "festival").param("chip", "popular800"))
                .andExpect(jsonPath("$.items[*].name").value(contains("토요일 축제", "겨울 축제")));
        mockMvc.perform(get("/api/contents").param("tab", "festival").param("chip", "all"))
                .andExpect(jsonPath("$.items.length()").value(4));
    }

    @Test
    @DisplayName("weekend 칩: 평일이면 다가오는 토·일, 토요일이면 오늘·내일, 일요일이면 오늘과 기간이 겹치는 축제")
    void weekendChip() throws Exception {
        clock.setNow(LocalDateTime.of(2026, 11, 11, 12, 0)); // 수요일 → 11-14(토)~11-15(일)
        mockMvc.perform(get("/api/contents").param("tab", "festival").param("chip", "weekend"))
                .andExpect(jsonPath("$.items[*].name").value(contains("가을 축제", "토요일 축제")));

        clock.setNow(LocalDateTime.of(2026, 11, 21, 12, 0)); // 토요일 → 11-21~11-22 (다음 주 토요일이 아니다)
        mockMvc.perform(get("/api/contents").param("tab", "festival").param("chip", "weekend"))
                .andExpect(jsonPath("$.items[*].name").value(contains("다음주 축제")));

        clock.setNow(LocalDateTime.of(2026, 11, 15, 12, 0)); // 일요일 → 11-15 하루 (토요일 축제는 어제 끝났다)
        mockMvc.perform(get("/api/contents").param("tab", "festival").param("chip", "weekend"))
                .andExpect(jsonPath("$.items[*].name").value(contains("가을 축제")));
    }

    @Test
    @DisplayName("open_now 칩은 지금 운영 시간 안이고 오늘이 쉬는 날이 아닌 곳만 준다")
    void openNowChip() throws Exception {
        mockMvc.perform(get("/api/contents").param("tab", "place").param("chip", "open_now"))
                .andExpect(jsonPath("$.items[*].name").value(contains("경복궁", "덕수궁", "박물관")));

        clock.setNow(LocalDateTime.of(2026, 11, 10, 9, 30)); // 화요일 09:30: 경복궁 휴무, 덕수궁·박물관은 10시 개장
        mockMvc.perform(get("/api/contents").param("tab", "place").param("chip", "open_now"))
                .andExpect(jsonPath("$.items").value(empty()));

        clock.setNow(LocalDateTime.of(2026, 11, 9, 17, 30)); // 월요일 17:30: 덕수궁은 닫았고 경복궁은 곧 마감
        mockMvc.perform(get("/api/contents").param("tab", "place").param("chip", "open_now"))
                .andExpect(jsonPath("$.items[*].name").value(contains("경복궁", "박물관")))
                .andExpect(jsonPath("$.items[0].openStatus").value("soon"));
    }

    @Test
    @DisplayName("분류 칩(고궁·박물관·아이와 함께)과 night 칩")
    void classificationAndNightChips() throws Exception {
        mockMvc.perform(get("/api/contents").param("tab", "place").param("chip", "palace_village"))
                .andExpect(jsonPath("$.items[*].name").value(contains("경복궁", "덕수궁")));
        mockMvc.perform(get("/api/contents").param("tab", "place").param("chip", "museum_exhibit"))
                .andExpect(jsonPath("$.items[*].name").value(contains("박물관")));
        mockMvc.perform(get("/api/contents").param("tab", "place").param("chip", "view_park"))
                .andExpect(jsonPath("$.items").value(empty()));
        mockMvc.perform(get("/api/contents").param("tab", "place").param("chip", "with_kids"))
                .andExpect(jsonPath("$.items[*].name").value(contains("박물관")));
        mockMvc.perform(get("/api/contents").param("tab", "place").param("chip", "night"))
                .andExpect(jsonPath("$.items[*].name").value(contains("박물관")));
        mockMvc.perform(get("/api/contents").param("tab", "festival").param("chip", "night"))
                .andExpect(jsonPath("$.items[*].name").value(contains("가을 축제")));
    }

    @Test
    @DisplayName("bbox는 지도 범위 안만 주고, 탭별 개수도 같은 범위로 센다")
    void bbox() throws Exception {
        mockMvc.perform(get("/api/contents").param("tab", "place").param("bbox", "37.5,126.9,37.6,127.0"))
                .andExpect(jsonPath("$.items[*].name").value(contains("경복궁", "덕수궁")))
                .andExpect(jsonPath("$.counts.place").value(2))
                // 축제 기본 좌표(서울시청)는 이 범위 안이고, 진행 중·예정 축제만 센다
                .andExpect(jsonPath("$.counts.festival").value(4));
    }

    @Test
    @DisplayName("q는 특수문자를 지운 뒤 이름·주소에서 찾고, 지운 뒤 2자 미만이면 400이다")
    void keyword() throws Exception {
        mockMvc.perform(get("/api/contents").param("tab", "place").param("q", " 경복!! "))
                .andExpect(jsonPath("$.items[*].name").value(contains("경복궁")))
                .andExpect(jsonPath("$.counts.place").value(1))
                .andExpect(jsonPath("$.counts.festival").value(0));
        mockMvc.perform(get("/api/contents").param("tab", "place").param("q", "주소 덕수"))
                .andExpect(jsonPath("$.items[*].name").value(contains("덕수궁")));
        mockMvc.perform(get("/api/contents").param("tab", "place").param("q", "경%!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("검색어는 2자 이상 입력해주세요."));
    }

    @Test
    @DisplayName("regionId는 시·도면 그 아래 전체, 시·군·구면 그 구만 거른다")
    void regionFilter() throws Exception {
        Long seoul = ContentFixtures.regionId(regions, "서울");
        Long jongno = ContentFixtures.regionId(regions, "서울 종로구");
        mockMvc.perform(get("/api/contents").param("tab", "place").param("regionId", seoul.toString()))
                .andExpect(jsonPath("$.items.length()").value(3));
        mockMvc.perform(get("/api/contents").param("tab", "place").param("regionId", jongno.toString()))
                .andExpect(jsonPath("$.items[*].name").value(contains("경복궁")))
                .andExpect(jsonPath("$.counts.festival").value(1));
    }

    @Test
    @DisplayName("categoryId는 그 탭의 필터 카테고리만 받는다")
    void categoryFilter() throws Exception {
        Long history = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE code = 'history'", Long.class);
        Long festivalCategory = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE code = 'festival'", Long.class);
        mockMvc.perform(get("/api/contents").param("tab", "place").param("categoryId", history.toString()))
                .andExpect(jsonPath("$.items[*].name").value(contains("경복궁", "덕수궁")));
        mockMvc.perform(get("/api/contents").param("tab", "festival").param("categoryId", festivalCategory.toString()))
                .andExpect(jsonPath("$.items.length()").value(4));
        mockMvc.perform(get("/api/contents").param("tab", "place").param("categoryId", festivalCategory.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("잘못된 필터 값입니다."));
    }

    @Test
    @DisplayName("탭에 맞지 않거나 없는 필터 값은 400 '잘못된 필터 값입니다.'다")
    void invalidFilters() throws Exception {
        expectInvalidFilter(get("/api/contents"));
        expectInvalidFilter(get("/api/contents").param("tab", "festival").param("sort", "name"));
        expectInvalidFilter(get("/api/contents").param("tab", "place").param("sort", "latest"));
        expectInvalidFilter(get("/api/contents").param("tab", "place").param("chip", "ongoing"));
        expectInvalidFilter(get("/api/contents").param("tab", "festival").param("chip", "open_now"));
        expectInvalidFilter(get("/api/contents").param("tab", "place").param("status", "ONGOING"));
        expectInvalidFilter(get("/api/contents").param("tab", "place").param("regionId", "999999"));
        expectInvalidFilter(get("/api/contents").param("tab", "place").param("bbox", "37.5,126.9,37.6"));
        expectInvalidFilter(get("/api/contents").param("tab", "place").param("bbox", "37.6,126.9,37.5,127.0"));
        expectInvalidFilter(get("/api/contents").param("tab", "place").param("size", "51"));
        expectInvalidFilter(get("/api/contents").param("tab", "place").param("size", "0"));
        mockMvc.perform(get("/api/contents").param("tab", "beach"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("풀 수 없는 커서나 다른 정렬의 커서는 400 INVALID_CURSOR다")
    void invalidCursor() throws Exception {
        mockMvc.perform(get("/api/contents").param("tab", "place").param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("목록을 처음부터 다시 불러옵니다."));

        JsonNode byName = read(get("/api/contents").param("tab", "place").param("sort", "name").param("size", "1"));
        mockMvc.perform(get("/api/contents").param("tab", "place").param("sort", "popular")
                        .param("cursor", byName.path("nextCursor").asString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("목록을 처음부터 다시 불러옵니다."));
        mockMvc.perform(get("/api/contents").param("tab", "festival").param("sort", "name")
                        .param("cursor", byName.path("nextCursor").asString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인기순 첫 페이지를 받은 뒤 인기 점수 버전이 오르면 다음 페이지는 409다")
    void popularCursorAfterVersionBump() throws Exception {
        JsonNode first = read(get("/api/contents").param("tab", "place").param("size", "1"));
        redisTemplate.opsForValue().set("popularity:version", "1");

        mockMvc.perform(get("/api/contents").param("tab", "place").param("size", "1")
                        .param("cursor", first.path("nextCursor").asString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("목록이 갱신되었습니다. 처음부터 다시 불러옵니다."));

        JsonNode fresh = read(get("/api/contents").param("tab", "place").param("size", "1"));
        mockMvc.perform(get("/api/contents").param("tab", "place").param("size", "1")
                        .param("cursor", fresh.path("nextCursor").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("덕수궁")));
    }

    private void expectInvalidFilter(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("잘못된 필터 값입니다."));
    }

    private JsonNode read(RequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> names(JsonNode page) {
        return page.path("items").valueStream().map(item -> item.path("name").asString()).toList();
    }

    private static TourIntro intro(String useTime, String restDate, String playTime) {
        return new TourIntro(useTime, restDate, playTime, null, null, null, null, null, null, null);
    }
}
