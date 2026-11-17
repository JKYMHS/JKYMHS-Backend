package com.jkymhs.backend.domain.calendar;

import com.jkymhs.backend.domain.calendar.client.google.GoogleCalendarClient;
import com.jkymhs.backend.domain.calendar.crypto.CalendarTokenCipher;
import com.jkymhs.backend.domain.calendar.entity.GoogleCalendarLink;
import com.jkymhs.backend.domain.calendar.repository.GoogleCalendarLinkRepository;
import com.jkymhs.backend.domain.calendar.worker.CalendarSyncWorker;
import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 캘린더 API 테스트 공통 준비물. 실제 구글 대신 MockRestServiceServer가 응답한다:
 * 구글 캘린더 클라이언트 빈의 RestClient를 목 서버에 붙인 것으로 바꿔 끼우고, 끝나면 되돌린다 (RouteApiTest와 같은 방식).
 * 작업자는 @Scheduled를 기다리지 않고 {@code worker.runOnce()}로 직접 돌린다 (테스트의 실행기는 동기).
 */
public abstract class CalendarApiTestSupport extends TripApiTestSupport {

    protected static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    protected static final String REVOKE_URI = "https://oauth2.googleapis.com/revoke";
    protected static final String USERINFO_URI = "https://openidconnect.googleapis.com/v1/userinfo";
    protected static final String API = "https://www.googleapis.com/calendar/v3";
    protected static final String REFRESH_TOKEN = "refresh-token-1";
    protected static final String ACCESS_TOKEN = "access-token-1";
    protected static final String GOOGLE_EMAIL = "me@gmail.com";

    @Autowired
    protected GoogleCalendarClient googleCalendarClient;

    @Autowired
    protected CalendarTokenCipher tokenCipher;

    @Autowired
    protected GoogleCalendarLinkRepository linkRepository;

    @Autowired
    protected CalendarSyncWorker worker;

    protected MockRestServiceServer google;
    private Object originalRestClient;

    @BeforeEach
    void bindGoogle() {
        originalRestClient = ReflectionTestUtils.getField(googleCalendarClient, "restClient");
        RestClient.Builder builder = RestClient.builder();
        google = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(googleCalendarClient, "restClient", builder.build());
    }

    @AfterEach
    void restoreGoogle() {
        ReflectionTestUtils.setField(googleCalendarClient, "restClient", originalRestClient);
    }

    // ---------- 연동 ----------

    protected GoogleCalendarLink link(User user) {
        return linkRepository.save(GoogleCalendarLink.create(user.getId(), GOOGLE_EMAIL, tokenCipher.encrypt(REFRESH_TOKEN),
                "https://www.googleapis.com/auth/calendar.events", LocalDateTime.now(clock)));
    }

    protected String linkStatus(Long userId) {
        return jdbcTemplate.queryForObject("SELECT status FROM google_calendar_links WHERE user_id = ?", String.class,
                userId);
    }

    protected long linkCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM google_calendar_links", Long.class);
    }

    // ---------- 구글 응답 ----------

    /** 리프레시 토큰 → 액세스 토큰 */
    protected void expectRefresh() {
        google.expect(requestTo(TOKEN_URI)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andExpect(content().string(containsString("refresh_token=" + REFRESH_TOKEN)))
                .andRespond(withSuccess("""
                        {"access_token": "%s", "expires_in": 3599, "token_type": "Bearer"}
                        """.formatted(ACCESS_TOKEN), MediaType.APPLICATION_JSON));
    }

    protected ResponseActions expectGoogle(HttpMethod httpMethod, String url) {
        return google.expect(requestTo(url)).andExpect(method(httpMethod))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN));
    }

    protected static String events(String calendarId) {
        return API + "/calendars/" + calendarId + "/events";
    }

    protected static String event(String calendarId, String eventId) {
        return events(calendarId) + "/" + eventId;
    }

    protected static String eventId(long itemId) {
        return "edgl" + itemId;
    }

    protected static String calendarListJson() {
        return """
                {"items": [
                  {"id": "me@gmail.com", "summary": "me@gmail.com", "primary": true, "accessRole": "owner"},
                  {"id": "family@group.calendar.google.com", "summary": "가족", "summaryOverride": "우리 가족", "accessRole": "writer"},
                  {"id": "ko.south_korea#holiday@group.v.calendar.google.com", "summary": "대한민국의 휴일", "accessRole": "reader"}
                ]}""";
    }

    // ---------- 여행 ----------

    /** 확정된 여행(기본 코스)에 장소를 넣고 [tripId, planId]를 돌려준다. itemsJson은 {@link #timed}·{@link #item} 들 */
    protected long[] tripWith(String token, LocalDate start, LocalDate end, String itemsJson) throws Exception {
        long[] ids = createTrip(token, "부산 여행", start, end);
        savePlan(token, ids[0], ids[1], 0, itemsJson).andExpect(status().isOk());
        return ids;
    }

    protected static String timed(long contentId, int day, int sort, String start, String end, String memo) {
        return """
                {"itemId": null, "contentId": %d, "dayNumber": %d, "sortOrder": %d, "startTime": %s, "endTime": %s, "memo": %s}"""
                .formatted(contentId, day, sort, quote(start), quote(end), quote(memo));
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /** 이 항목을 primary 캘린더에 우리 방식의 일정 id로 올린 것처럼 만든다 */
    protected void uploaded(long itemId) {
        markSynced(itemId, eventId(itemId));
    }

    protected void loadPlaces() {
        load(place(101L, "경복궁", 37.5796, 126.9770), place(102L, "창덕궁", 37.5794, 126.9910),
                place(103L, "북촌한옥마을", 37.5826, 126.9831));
    }

    // ---------- 작업 ----------

    protected Map<String, Object> job(long jobId) {
        return jdbcTemplate.queryForMap("SELECT * FROM calendar_sync_jobs WHERE id = ?", jobId);
    }

    protected Long latestJobId() {
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM calendar_sync_jobs", Long.class);
    }

    protected long jobCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM calendar_sync_jobs", Long.class);
    }

    protected java.util.List<Map<String, Object>> jobItems(long jobId) {
        return jdbcTemplate.queryForList("SELECT * FROM calendar_sync_job_items WHERE job_id = ? ORDER BY id", jobId);
    }
}
