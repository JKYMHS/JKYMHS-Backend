package com.jkymhs.backend.domain.content.batch;

import com.jkymhs.backend.domain.category.service.CategorySeeder;
import com.jkymhs.backend.domain.content.ContentFixtures;
import com.jkymhs.backend.domain.content.client.TourApiClient;
import com.jkymhs.backend.domain.content.client.TourApiProperties;
import com.jkymhs.backend.domain.content.client.TourApiResponses;
import com.jkymhs.backend.domain.content.entity.BatchJobLog;
import com.jkymhs.backend.domain.content.entity.BatchJobName;
import com.jkymhs.backend.domain.content.entity.BatchJobStatus;
import com.jkymhs.backend.domain.content.entity.TourSyncCursorName;
import com.jkymhs.backend.domain.content.service.BatchJobLogService;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.content.service.TourClassificationService;
import com.jkymhs.backend.domain.content.service.TourSyncCursorService;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.support.ClockTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static com.jkymhs.backend.domain.content.ContentFixtures.festival;
import static com.jkymhs.backend.domain.content.client.TourApiResponses.item;
import static com.jkymhs.backend.domain.content.client.TourApiResponses.page;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * TourAPI는 MockRestServiceServer가 흉내 낸다. 배치 빈 대신 테스트용 클라이언트를 넣은 TourSyncJob을 직접 만들어 run()을 부른다.
 * 시계는 2026-11-09 10:00(KST)이고, 기준일(MODIFIED_SINCE)은 2026-11-01에서 시작한다.
 */
class TourSyncJobTest extends ClockTestSupport {

    private static final String BASE_URL = "https://tour.test/KorService2";
    private static final String WEBHOOK_URL = "https://hooks.test/alert";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    @Autowired
    private ContentService contentService;

    @Autowired
    private RegionService regionService;

    @Autowired
    private TourClassificationService tourClassificationService;

    @Autowired
    private CategorySeeder categorySeeder;

    @Autowired
    private TourSyncCursorService cursorService;

    @Autowired
    private BatchJobLogService batchJobLogService;

    private MockRestServiceServer tourApi;
    private MockRestServiceServer webhook;

    @BeforeEach
    void setUp() {
        regionService.upsertAll(ContentFixtures.REGIONS);
        tourClassificationService.upsertAll(ContentFixtures.CLASSIFICATIONS);
        categorySeeder.seed();
    }

    @Test
    @DisplayName("바뀐 콘텐츠를 upsert하고 showflag=0은 삭제 표시하며, 기준일을 오늘로 옮기고 후보 캐시를 지운다")
    void syncChanges() {
        seedExisting();
        redisTemplate.opsForValue().set("candidates:11:2026-11-10", "[1,2]");
        redisTemplate.opsForValue().set("candidates:26:2026-11-10", "[3]");
        redisTemplate.opsForValue().set("popularity:version", "7");
        TourSyncJob job = job(0);
        expectFestivalSearch(page(listItem("301", "15", "새 축제", "20261120", "20261122")));
        expectSyncList(12, page(
                listItem("100", "12", "경복궁(이름 바뀜)", "", ""),
                item("contentid", "200", "contenttypeid", "12", "showflag", "0"),
                item("contentid", "400", "contenttypeid", "12", "title", "좌표 없음", "showflag", "1")));
        expectSyncList(14, page());
        expectSyncList(15, page(
                listItem("301", "15", "새 축제", "", ""),
                listItem("300", "15", "있던 축제(이름 바뀜)", "", ""),
                listItem("302", "15", "기간 모르는 축제", "", "")));
        expectSyncList(32, page());
        expectSyncList(39, page());

        TourSyncResult result = job.run();

        tourApi.verify();
        assertThat(result).isEqualTo(new TourSyncResult(BatchJobStatus.SUCCESS, 3, 1, 2, 0, 0, 6));
        Map<Long, ContentSnapshot> snapshots = contentService.findSnapshots(List.of(100L, 200L, 300L, 301L, 302L));
        assertThat(snapshots).containsOnlyKeys(100L, 200L, 300L, 301L);
        assertThat(snapshots.get(100L).name()).isEqualTo("경복궁(이름 바뀜)");
        assertThat(snapshots.get(200L).deleted()).isTrue();
        assertThat(snapshots.get(301L).eventStartDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(snapshots.get(300L).name()).isEqualTo("있던 축제(이름 바뀜)");
        assertThat(snapshots.get(300L).eventEndDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(cursorService.get(TourSyncCursorName.MODIFIED_SINCE)).contains("2026-11-09");
        assertThat(redisTemplate.keys("candidates:*")).isEmpty();
        assertThat(redisTemplate.opsForValue().get("popularity:version")).isEqualTo("7");
        BatchJobLog log = latestLog();
        assertThat(log.getStatus()).isEqualTo(BatchJobStatus.SUCCESS);
        assertThat(log.getProcessedCount()).isEqualTo(4);
        assertThat(log.getApiCallCount()).isEqualTo(6);
        assertThat(log.getFinishedAt()).isEqualTo(LocalDateTime.of(TODAY, LocalTime.of(10, 0)));
    }

    @Test
    @DisplayName("변경분을 받다가 호출 한도 초과(XML)가 오면 PARTIAL로 끝나고 기준일을 옮기지 않는다")
    void quotaExceededDuringChanges() {
        seedExisting();
        TourSyncJob job = job(10);
        expectFestivalSearch(page());
        expectSyncList(12, page(listItem("100", "12", "경복궁(이름 바뀜)", "", "")));
        tourApi.expect(requestTo(startsWith(BASE_URL + "/areaBasedSyncList2?")))
                .andExpect(queryParam("contentTypeId", "14"))
                .andRespond(withSuccess(TourApiResponses.QUOTA_XML,
                        MediaType.TEXT_XML));

        TourSyncResult result = job.run();

        tourApi.verify();
        assertThat(result.status()).isEqualTo(BatchJobStatus.PARTIAL);
        assertThat(result.upserted()).isEqualTo(1);
        assertThat(cursorService.get(TourSyncCursorName.MODIFIED_SINCE)).contains("2026-11-01");
        assertThat(latestLog().getStatus()).isEqualTo(BatchJobStatus.PARTIAL);
        assertThat(latestLog().getErrorMessage()).contains("한도 초과");
    }

    @Test
    @DisplayName("운영 정보를 안 받은 관광지·축제는 detailIntro2로 받아 운영 시간·쉬는 날·공연 시간 컬럼을 채운다")
    void introBackfill() {
        seedExisting();
        TourSyncJob job = job(10);
        expectFestivalSearch(page());
        for (int typeId : TourSyncJob.TOUR_TYPE_IDS) {
            expectSyncList(typeId, page());
        }
        expectIntro("100", "12", page(item("contentid", "100", "usetime", "09:00~18:00", "restdate", "매주 화요일")));
        expectIntro("200", "12", page(item("contentid", "200", "usetime", "10:00~17:00", "restdate", "연중무휴")));
        expectIntro("300", "15", page(item("contentid", "300", "playtime", "18:00~22:00", "eventplace", "청계천")));

        TourSyncResult result = job.run();

        tourApi.verify();
        assertThat(result.status()).isEqualTo(BatchJobStatus.SUCCESS);
        assertThat(result.introUpdated()).isEqualTo(3);
        Map<Long, ContentSnapshot> snapshots = contentService.findSnapshots(List.of(100L, 300L));
        assertThat(snapshots.get(100L).openTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(snapshots.get(100L).restDays()).containsExactly(DayOfWeek.TUESDAY);
        assertThat(snapshots.get(300L).eventStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(contentService.findIntroTargets(10, LocalDateTime.of(TODAY, LocalTime.MIN))).isEmpty();
        assertThat(cursorService.get(TourSyncCursorName.INTRO_LAST_ID)).contains("300");
    }

    @Test
    @DisplayName("재시도 후에도 5xx면 FAILED로 기록하고 웹훅으로 알림을 보내며 기준일은 그대로다")
    void failureSendsAlert() {
        seedExisting();
        TourSyncJob job = job(0);
        tourApi.expect(ExpectedCount.times(3), requestTo(startsWith(BASE_URL + "/searchFestival2?")))
                .andRespond(withServerError());
        webhook.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("TOUR_SYNC")))
                .andRespond(withSuccess());

        TourSyncResult result = job.run();

        tourApi.verify();
        webhook.verify();
        assertThat(result.status()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(latestLog().getStatus()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(latestLog().getErrorMessage()).contains("searchFestival2");
        assertThat(cursorService.get(TourSyncCursorName.MODIFIED_SINCE)).contains("2026-11-01");
    }

    @Test
    @DisplayName("콘텐츠도 기준일도 없으면(CSV 적재 전) TourAPI를 부르지 않고 기록도 남기지 않는다")
    void skipBeforeInitialImport() {
        TourSyncJob job = job(10);

        TourSyncResult result = job.run();

        tourApi.verify();
        assertThat(result.status()).isNull();
        assertThat(batchJobLogService.findRecent(BatchJobName.TOUR_SYNC)).isEmpty();
    }

    // 경복궁(100)·덕수궁(200) 관광지, 기간이 있는 축제(300). 기준일 2026-11-01
    private void seedExisting() {
        contentService.upsert(List.of(
                attraction(100L, "경복궁", "11", "110"),
                attraction(200L, "덕수궁", "11", "140"),
                festival(300L, "있던 축제", "11", "140", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        ), contentService.loadContext());
        cursorService.set(TourSyncCursorName.MODIFIED_SINCE, "2026-11-01");
    }

    private TourSyncJob job(int introPerRun) {
        RestClient.Builder tourBuilder = RestClient.builder();
        tourApi = MockRestServiceServer.bindTo(tourBuilder).ignoreExpectOrder(true).build();
        RestClient.Builder webhookBuilder = RestClient.builder();
        webhook = MockRestServiceServer.bindTo(webhookBuilder).build();
        TourApiProperties properties = new TourApiProperties(BASE_URL, "test-key", 1000, Duration.ofSeconds(10),
                Duration.ZERO, new TourApiProperties.Sync("0 0 4 * * *", introPerRun));
        TourApiClient client = new TourApiClient(tourBuilder, properties, redisTemplate, clock);
        BatchAlertNotifier notifier = new BatchAlertNotifier(webhookBuilder, new BatchProperties(true, WEBHOOK_URL));
        return new TourSyncJob(client, properties, contentService, cursorService, batchJobLogService, notifier,
                redisTemplate, clock);
    }

    private void expectFestivalSearch(String body) {
        tourApi.expect(requestTo(startsWith(BASE_URL + "/searchFestival2?")))
                .andExpect(queryParam("modifiedtime", "20261101"))
                .andExpect(queryParam("eventStartDate", "20251109"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void expectSyncList(int contentTypeId, String body) {
        tourApi.expect(requestTo(startsWith(BASE_URL + "/areaBasedSyncList2?")))
                .andExpect(queryParam("contentTypeId", String.valueOf(contentTypeId)))
                .andExpect(queryParam("modifiedtime", "20261101"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void expectIntro(String contentId, String contentTypeId, String body) {
        tourApi.expect(requestTo(startsWith(BASE_URL + "/detailIntro2?")))
                .andExpect(queryParam("contentId", contentId))
                .andExpect(queryParam("contentTypeId", contentTypeId))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private BatchJobLog latestLog() {
        return batchJobLogService.findRecent(BatchJobName.TOUR_SYNC).getFirst();
    }

    // 서울 종로구(11/110) 좌표가 있는 목록 항목
    private static String listItem(String id, String typeId, String title, String start, String end) {
        return item("contentid", id, "contenttypeid", typeId, "title", title, "addr1", "서울 종로구",
                "mapx", "126.9767", "mapy", "37.5760", "lDongRegnCd", "11", "lDongSignguCd", "110",
                "lclsSystm1", typeId.equals("15") ? "EV" : "HS", "lclsSystm2", typeId.equals("15") ? "EV01" : "HS01",
                "lclsSystm3", typeId.equals("15") ? "EV010100" : "HS010100", "showflag", "1",
                "eventstartdate", start, "eventenddate", end, "modifiedtime", "20261108120000");
    }
}
