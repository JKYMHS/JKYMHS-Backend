package com.jkymhs.backend.domain.trip;

import com.jayway.jsonpath.JsonPath;
import com.jkymhs.backend.domain.auth.service.TokenService;
import com.jkymhs.backend.domain.category.service.CategorySeeder;
import com.jkymhs.backend.domain.content.ContentFixtures;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSource;
import com.jkymhs.backend.domain.content.service.TourClassificationService;
import com.jkymhs.backend.domain.content.service.TourIntro;
import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.repository.UserRepository;
import com.jkymhs.backend.domain.user.service.UserConsentService;
import com.jkymhs.backend.support.ClockTestSupport;
import com.jkymhs.backend.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 여행 API 테스트 공통 준비물. 시계는 2026-11-09(월) 10:00 KST로 고정된다 (ClockTestSupport).
 * 지역(서울·종로구·중구·부산·해운대구·세종)과 분류를 매 테스트 전에 넣는다 (테스트가 끝나면 모든 테이블을 비우기 때문).
 *
 * 지난 여행은 API로만 만든다: 시계를 그 여행 시작일로 옮겨서 만들고 다시 오늘로 되돌린다 (지난 날짜로는 여행을 만들 수 없어서).
 */
@RecordApplicationEvents
public abstract class TripApiTestSupport extends ClockTestSupport {

    protected static final LocalDate TODAY = MutableClock.DEFAULT_NOW.toLocalDate();
    protected static final String PASSWORD = "abc12345";

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TokenService tokenService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected UserConsentService userConsentService;

    @Autowired
    protected RegionService regionService;

    @Autowired
    protected TourClassificationService tourClassificationService;

    @Autowired
    protected CategorySeeder categorySeeder;

    @Autowired
    protected ContentService contentService;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ApplicationEvents events;

    protected Long seoulId;
    protected Long jongnoId;
    protected Long busanId;

    @BeforeEach
    void seedRegionsAndClassifications() {
        regionService.upsertAll(ContentFixtures.REGIONS);
        tourClassificationService.upsertAll(ContentFixtures.CLASSIFICATIONS);
        categorySeeder.seed();
        List<RegionRef> regions = regionService.getAllRegions();
        seoulId = ContentFixtures.regionId(regions, "서울");
        jongnoId = ContentFixtures.regionId(regions, "서울 종로구");
        busanId = ContentFixtures.regionId(regions, "부산");
    }

    // ---------- 회원 ----------

    /** 필수 약관 + 주어진 선택 항목에 동의한 이메일 회원 */
    protected User saveUser(String email, ConsentType... optional) {
        User user = userRepository.save(User.createWithPassword(email, passwordEncoder.encode(PASSWORD)));
        Set<ConsentType> agreed = EnumSet.of(ConsentType.AGE_14, ConsentType.TERMS, ConsentType.PRIVACY);
        agreed.addAll(List.of(optional));
        userConsentService.saveInitialConsents(user, agreed);
        return user;
    }

    protected String token(User user) {
        return tokenService.issue(user, "test").accessToken();
    }

    // ---------- 콘텐츠 ----------

    /** 서울 종로구 관광지 (궁). 좌표를 정할 수 있다 */
    protected static ContentSource place(long id, String name, double lat, double lng) {
        return source(id, ContentType.ATTRACTION, name, lat, lng, "HS", "HS01", "HS010100", null, null);
    }

    protected static ContentSource restaurant(long id, String name, double lat, double lng) {
        return source(id, ContentType.RESTAURANT, name, lat, lng, "FD", "FD01", "FD010100", null, null);
    }

    protected static ContentSource festival(long id, String name, double lat, double lng, LocalDate start, LocalDate end) {
        return source(id, ContentType.FESTIVAL, name, lat, lng, "EV", "EV01", "EV010100", start, end);
    }

    private static ContentSource source(long id, ContentType type, String name, double lat, double lng, String lcls1,
                                        String lcls2, String lcls3, LocalDate start, LocalDate end) {
        int tourTypeId = switch (type) {
            case ATTRACTION -> 12;
            case FESTIVAL -> 15;
            case RESTAURANT -> 39;
            case LODGING -> 32;
        };
        return new ContentSource(id, type, tourTypeId, name, "주소 " + name, BigDecimal.valueOf(lat),
                BigDecimal.valueOf(lng), "11", "110", lcls1, lcls2, lcls3, "https://img.test/" + id + ".jpg",
                "02-000-0000", start, end, LocalDateTime.of(2026, 11, 1, 9, 0));
    }

    protected void load(ContentSource... sources) {
        contentService.upsert(List.of(sources), contentService.loadContext());
    }

    /** 운영 시간("09:00~18:00 (입장마감 17:00)")과 쉬는 날("매주 화요일") */
    protected void hours(long contentId, String useTime, String restDate) {
        contentService.applyIntro(contentId, new TourIntro(useTime, restDate, null, null, null, null, null, null,
                null, null));
    }

    // ---------- 여행 ----------

    /** API로 여행을 만들고 [tripId, 기본 일정안 id]를 돌려준다. 시작일이 지났으면 시계를 잠깐 그날로 옮긴다 */
    protected long[] createTrip(String token, String title, LocalDate start, LocalDate end) throws Exception {
        return createTrip(token, """
                {"title": "%s", "startDate": "%s", "endDate": "%s", "regionIds": [%d], "companion": "friends"}
                """.formatted(title, start, end, seoulId), start);
    }

    protected long[] createTrip(String token, String body, LocalDate start) throws Exception {
        if (start.isBefore(TODAY)) {
            clock.setNow(start.atTime(9, 0));
        }
        try {
            MvcResult result = mockMvc.perform(json(post("/api/trips"), token, body))
                    .andExpect(status().isCreated()).andReturn();
            return new long[]{longAt(result, "$.id"), longAt(result, "$.planId")};
        } finally {
            clock.reset();
        }
    }

    /** 일정안 전체 저장. items는 {@link #item} 들을 쉼표로 이은 것 */
    protected ResultActions savePlan(String token, long tripId, long planId, int version, String items)
            throws Exception {
        return mockMvc.perform(json(put("/api/trips/{tripId}/plans/{planId}", tripId, planId), token, """
                {"version": %d, "name": "기본 코스", "items": [%s]}
                """.formatted(version, items)));
    }

    protected static String item(Long itemId, long contentId, int day, int sort) {
        return """
                {"itemId": %s, "contentId": %d, "dayNumber": %d, "sortOrder": %d}""".formatted(itemId, contentId, day, sort);
    }

    protected static String items(String... items) {
        return String.join(", ", items);
    }

    protected MvcResult planDetail(String token, long tripId, long planId) throws Exception {
        return mockMvc.perform(auth(get("/api/trips/{tripId}/plans/{planId}", tripId, planId), token))
                .andExpect(status().isOk()).andReturn();
    }

    protected int planVersion(String token, long tripId, long planId) throws Exception {
        return ((Number) JsonPath.read(body(planDetail(token, tripId, planId)), "$.version")).intValue();
    }

    /** 일정안의 항목 id들 (날짜·순서 순) */
    protected List<Long> itemIds(long planId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM plan_items WHERE plan_id = ? ORDER BY day_number, sort_order", Long.class, planId);
    }

    /** 캘린더 도메인이 업로드를 끝낸 것처럼 만든다 */
    protected void markSynced(long itemId, String eventId) {
        jdbcTemplate.update("UPDATE plan_items SET calendar_id = 'primary', calendar_event_id = ?, "
                + "sync_status = 'SYNCED', synced_at = NOW() WHERE id = ?", eventId, itemId);
    }

    protected String syncStatus(long itemId) {
        return jdbcTemplate.queryForObject("SELECT sync_status FROM plan_items WHERE id = ?", String.class, itemId);
    }

    protected <T> List<T> eventsOf(Class<T> type) {
        return events.stream(type).collect(Collectors.toList());
    }

    // ---------- HTTP ----------

    protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token, String body) {
        return auth(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected static long longAt(MvcResult result, String path) throws Exception {
        return ((Number) JsonPath.read(body(result), path)).longValue();
    }
}
