package com.jkymhs.backend.domain.content.controller;

import com.jkymhs.backend.domain.category.service.CategorySeeder;
import com.jkymhs.backend.domain.content.ContentFixtures;
import com.jkymhs.backend.domain.content.client.TourApiClient;
import com.jkymhs.backend.domain.content.client.TourApiProperties;
import com.jkymhs.backend.domain.content.service.ContentDetailService;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.TourClassificationService;
import com.jkymhs.backend.domain.content.service.TourIntro;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.support.ClockTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static com.jkymhs.backend.domain.content.ContentFixtures.festival;
import static com.jkymhs.backend.domain.content.client.TourApiResponses.item;
import static com.jkymhs.backend.domain.content.client.TourApiResponses.page;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TourAPI는 MockRestServiceServer가 흉내 낸다. 상세 서비스 빈의 TourApiClient를 테스트용 클라이언트로 바꿔 끼우고
 * 끝나면 원래 빈으로 되돌린다 (빈을 @MockitoBean으로 바꾸면 스프링 컨텍스트가 하나 더 떠서 테스트가 느려진다).
 * 시계 기본값은 2026-11-09(월) 10:00 KST다. 1번은 관광지(경복궁), 2번은 축제다.
 */
class ContentDetailApiTest extends ClockTestSupport {

    private static final String BASE_URL = "https://tour.test/KorService2";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 10, 0);

    @Autowired
    private RegionService regionService;

    @Autowired
    private TourClassificationService tourClassificationService;

    @Autowired
    private CategorySeeder categorySeeder;

    @Autowired
    private ContentService contentService;

    @Autowired
    private ContentDetailService contentDetailService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockRestServiceServer tourApi;
    private Object originalClient;

    @BeforeEach
    void setUp() {
        regionService.upsertAll(ContentFixtures.REGIONS);
        tourClassificationService.upsertAll(ContentFixtures.CLASSIFICATIONS);
        categorySeeder.seed();
        contentService.upsert(List.of(
                attraction(1L, "경복궁", "11", "110"),
                festival(2L, "가을 축제", "11", "140", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15))
        ), contentService.loadContext());

        RestClient.Builder builder = RestClient.builder();
        tourApi = MockRestServiceServer.bindTo(builder).build();
        TourApiProperties properties = new TourApiProperties(BASE_URL, "test-key", 1000, Duration.ofSeconds(1),
                Duration.ZERO, new TourApiProperties.Sync("0 0 4 * * *", 0));
        ContentDetailService target = AopTestUtils.getUltimateTargetObject(contentDetailService);
        originalClient = ReflectionTestUtils.getField(target, "tourApiClient");
        ReflectionTestUtils.setField(target, "tourApiClient", new TourApiClient(builder, properties, redisTemplate, clock));
    }

    @AfterEach
    void restoreClient() {
        ContentDetailService target = AopTestUtils.getUltimateTargetObject(contentDetailService);
        ReflectionTestUtils.setField(target, "tourApiClient", originalClient);
    }

    @Test
    @DisplayName("7일 안에 받은 상세가 있으면 TourAPI를 부르지 않고 저장된 값을 준다")
    void freshCache() throws Exception {
        storeDetail(1L, "저장된 소개", NOW.minusDays(6));
        jdbcTemplate.update("INSERT INTO content_images (content_id, image_url, sort_order) VALUES (1, 'https://img.test/a.jpg', 1)");

        mockMvc.perform(get("/api/contents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("경복궁"))
                .andExpect(jsonPath("$.kind").value("attraction"))
                .andExpect(jsonPath("$.regionName").value("서울"))
                .andExpect(jsonPath("$.districtName").value("종로구"))
                .andExpect(jsonPath("$.overview").value("저장된 소개"))
                .andExpect(jsonPath("$.images").value(contains("https://img.test/a.jpg")))
                .andExpect(jsonPath("$.contact").value("02-000-0000"))
                .andExpect(jsonPath("$.detailAvailable").value(true))
                .andExpect(jsonPath("$.isStale").value(false))
                .andExpect(jsonPath("$.liked").value(false));
        tourApi.verify();
    }

    @Test
    @DisplayName("상세를 받은 적이 없는 행(운영 정보로만 만든 행)은 TourAPI 네 가지를 불러서 저장하고 새 값을 준다")
    void refreshWhenNeverFetched() throws Exception {
        contentService.applyIntro(1L, new TourIntro(null, null, null, null, null, "3,000원", null, null, null, null));
        expect("detailCommon2", page(item("contentid", "1", "overview", "조선의 법궁", "homepage", "https://royal.test")));
        tourApi.expect(requestTo(startsWith(BASE_URL + "/detailIntro2"))).andExpect(queryParam("contentTypeId", "12"))
                .andRespond(withSuccess(page(item("usetime", "09:00~18:00", "restdate", "매주 화요일", "usefee", "3,000원")),
                        MediaType.APPLICATION_JSON));
        expect("detailImage2", page(item("originimgurl", "https://img.test/1.jpg"), item("originimgurl", "https://img.test/2.jpg")));
        expect("detailPetTour2", page(item("acmpyTypeCd", "동반불가")));

        mockMvc.perform(get("/api/contents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview").value("조선의 법궁"))
                .andExpect(jsonPath("$.homepage").value("https://royal.test"))
                .andExpect(jsonPath("$.images").value(contains("https://img.test/1.jpg", "https://img.test/2.jpg")))
                .andExpect(jsonPath("$.operatingHours").value("09:00~18:00"))
                .andExpect(jsonPath("$.openTime").value("09:00"))
                .andExpect(jsonPath("$.closeTime").value("18:00"))
                .andExpect(jsonPath("$.restDays").value(contains("TUE")))
                .andExpect(jsonPath("$.restDateRaw").value("매주 화요일"))
                .andExpect(jsonPath("$.openStatus").value("open"))
                .andExpect(jsonPath("$.fee").value("3,000원"))
                .andExpect(jsonPath("$.petAllowed").value(false))
                .andExpect(jsonPath("$.detailAvailable").value(true))
                .andExpect(jsonPath("$.isStale").value(false));
        tourApi.verify();
        assertThat(jdbcTemplate.queryForObject("SELECT fetched_at FROM content_details WHERE content_id = 1",
                LocalDateTime.class)).isEqualTo(NOW);
        assertThat(redisTemplate.hasKey("lock:detail:1")).isFalse();
    }

    @Test
    @DisplayName("7일이 지난 축제 상세는 다시 받고, 반려동물 정보를 이미 알면 detailPetTour2는 부르지 않는다")
    void refreshWhenOld() throws Exception {
        storeDetail(2L, "옛 소개", NOW.minusDays(7));
        jdbcTemplate.update("UPDATE contents SET pet_allowed = 1 WHERE id = 2");
        expect("detailCommon2", page(item("contentid", "2", "overview", "새 소개")));
        expect("detailIntro2", page(item("playtime", "18:00~22:00", "eventplace", "청계광장")));
        expect("detailImage2", page());

        mockMvc.perform(get("/api/contents/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("festival"))
                .andExpect(jsonPath("$.status").value("ONGOING"))
                .andExpect(jsonPath("$.openStatus").value(nullValue()))
                .andExpect(jsonPath("$.overview").value("새 소개"))
                .andExpect(jsonPath("$.eventPlace").value("청계광장"))
                .andExpect(jsonPath("$.eventStartTime").value("18:00"))
                .andExpect(jsonPath("$.eventEndTime").value("22:00"))
                .andExpect(jsonPath("$.playtime").value("18:00~22:00"))
                .andExpect(jsonPath("$.images").value(empty()))
                .andExpect(jsonPath("$.petAllowed").value(true))
                .andExpect(jsonPath("$.isStale").value(false));
        tourApi.verify();
    }

    @Test
    @DisplayName("TourAPI가 실패해도 저장된 상세가 있으면 200으로 그 값을 주고 isStale=true다")
    void tourApiFailureWithStoredDetail() throws Exception {
        storeDetail(1L, "옛 소개", NOW.minusDays(30));
        tourApi.expect(ExpectedCount.times(3), requestTo(startsWith(BASE_URL + "/detailCommon2")))
                .andRespond(withServerError());

        mockMvc.perform(get("/api/contents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview").value("옛 소개"))
                .andExpect(jsonPath("$.detailAvailable").value(true))
                .andExpect(jsonPath("$.isStale").value(true));
        tourApi.verify();
    }

    @Test
    @DisplayName("TourAPI가 실패하고 저장된 상세도 없으면 200으로 기본 정보만 주고 detailAvailable=false다")
    void tourApiFailureWithoutStoredDetail() throws Exception {
        tourApi.expect(ExpectedCount.times(3), requestTo(startsWith(BASE_URL + "/detailCommon2")))
                .andRespond(withServerError());

        mockMvc.perform(get("/api/contents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("경복궁"))
                .andExpect(jsonPath("$.lat").value(37.5665))
                .andExpect(jsonPath("$.overview").value(nullValue()))
                .andExpect(jsonPath("$.images").value(empty()))
                .andExpect(jsonPath("$.detailAvailable").value(false))
                .andExpect(jsonPath("$.isStale").value(false));
        tourApi.verify();
    }

    @Test
    @DisplayName("소개글은 받았는데 사진 호출이 실패하면 받은 데까지만 저장한다")
    void partialFailure() throws Exception {
        jdbcTemplate.update("INSERT INTO content_images (content_id, image_url, sort_order) VALUES (1, 'https://img.test/old.jpg', 1)");
        expect("detailCommon2", page(item("contentid", "1", "overview", "새 소개")));
        expect("detailIntro2", page(item("usetime", "10:00~17:00")));
        tourApi.expect(ExpectedCount.times(3), requestTo(startsWith(BASE_URL + "/detailImage2")))
                .andRespond(withServerError());

        mockMvc.perform(get("/api/contents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview").value("새 소개"))
                .andExpect(jsonPath("$.openTime").value("10:00"))
                .andExpect(jsonPath("$.images").value(contains("https://img.test/old.jpg")))
                .andExpect(jsonPath("$.detailAvailable").value(true));
        tourApi.verify();
    }

    @Test
    @DisplayName("다른 요청이 락을 잡고 갱신 중이면 TourAPI를 부르지 않고 저장된 값을 준다")
    void lockHeld() throws Exception {
        storeDetail(1L, "옛 소개", NOW.minusDays(8));
        redisTemplate.opsForValue().set("lock:detail:1", "other-request", Duration.ofSeconds(10));

        mockMvc.perform(get("/api/contents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview").value("옛 소개"))
                .andExpect(jsonPath("$.isStale").value(true));
        tourApi.verify();
        assertThat(redisTemplate.opsForValue().get("lock:detail:1")).isEqualTo("other-request");
    }

    @Test
    @DisplayName("삭제됐거나 없는 콘텐츠는 404다")
    void notFound() throws Exception {
        contentService.markDeleted(List.of(1L));

        mockMvc.perform(get("/api/contents/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않거나 삭제된 장소입니다."));
        mockMvc.perform(get("/api/contents/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않거나 삭제된 장소입니다."));
        tourApi.verify();
    }

    private void expect(String operation, String body) {
        tourApi.expect(requestTo(startsWith(BASE_URL + "/" + operation)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void storeDetail(long id, String overview, LocalDateTime fetchedAt) {
        jdbcTemplate.update("INSERT INTO content_details (content_id, overview, fetched_at) VALUES (?, ?, ?)",
                id, overview, fetchedAt);
    }
}
