package com.jkymhs.backend.domain.content.batch;

import com.jkymhs.backend.domain.content.entity.BatchJobName;
import com.jkymhs.backend.domain.content.entity.BatchJobStatus;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.entity.IoType;
import com.jkymhs.backend.domain.content.entity.TourSyncCursorName;
import com.jkymhs.backend.domain.content.service.BatchJobLogService;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.content.service.TourSyncCursorService;
import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.jkymhs.backend.domain.content.ContentFixtures.regionId;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * src/test/resources/tourapi/의 작은 CSV(collect.py export와 같은 형식, UTF-8 BOM, CRLF)를 적재한다.
 */
class TourCsvImporterTest extends IntegrationTestSupport {

    @Autowired
    private TourCsvImporter importer;

    @Autowired
    private ContentService contentService;

    @Autowired
    private RegionService regionService;

    @Autowired
    private TourSyncCursorService cursorService;

    @Autowired
    private BatchJobLogService batchJobLogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("지역·분류·카테고리·콘텐츠·운영 정보를 넣고, 좌표·축제 기간이 없는 줄과 없는 콘텐츠의 상세는 건너뛴다")
    void importAll() throws Exception {
        TourImportResult result = importer.importFrom(fixtureDir());

        assertThat(result).isEqualTo(new TourImportResult(6, 12, 4, 2, 3, 1));
        assertThat(count("categories")).isEqualTo(13);

        List<RegionRef> regions = regionService.getAllRegions();
        Map<Long, ContentSnapshot> snapshots = contentService.findSnapshots(List.of(126508L, 129507L, 3113671L, 2800001L));
        ContentSnapshot palace = snapshots.get(126508L);
        assertThat(palace.regionId()).isEqualTo(regionId(regions, "서울 종로구"));
        assertThat(palace.categoryName()).isEqualTo("궁");
        // 실내외는 CSV의 io 칸이 아니라 classpath:seed/indoor_outdoor.csv(고궁 = MIXED)에서 정한다
        assertThat(palace.ioType()).isEqualTo(IoType.MIXED);
        assertThat(palace.openTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(palace.lastEntryTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(palace.restDays()).containsExactly(DayOfWeek.TUESDAY);
        assertThat(palace.lat()).isEqualTo(37.5760837);

        // 용산구(170)는 지역 목록에 없어서 시·도(서울)로 채운다
        ContentSnapshot museum = snapshots.get(129507L);
        assertThat(museum.regionId()).isEqualTo(regionId(regions, "서울"));
        assertThat(museum.restDays()).isEmpty();
        assertThat(museum.ioType()).isEqualTo(IoType.INDOOR);

        ContentSnapshot festival = snapshots.get(3113671L);
        assertThat(festival.name()).isEqualTo("서울빛초롱축제, 2026");
        assertThat(festival.eventStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(festival.eventStartTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(festival.eventEndTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(jdbcTemplate.queryForObject("SELECT event_place FROM contents WHERE id = 3113671", String.class))
                .isEqualTo("청계광장~삼일교");

        assertThat(snapshots.get(2800001L).contentType()).isEqualTo(ContentType.RESTAURANT);
        assertThat(jdbcTemplate.queryForObject("SELECT category_id FROM contents WHERE id = 2800001", Long.class)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT c.code FROM contents t JOIN categories c ON c.id = t.category_id WHERE t.id = 3113671", String.class))
                .isEqualTo("festival");

        assertThat(cursorService.get(TourSyncCursorName.MODIFIED_SINCE)).contains("2026-11-07");
        assertThat(batchJobLogService.findRecent(BatchJobName.TOUR_IMPORT))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getStatus()).isEqualTo(BatchJobStatus.SUCCESS);
                    assertThat(log.getProcessedCount()).isEqualTo(7);
                    assertThat(log.getFinishedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("두 번 적재해도 행 수와 지역 id가 그대로이고, 인기 점수처럼 다른 배치가 쓴 값은 남는다")
    void idempotent() throws Exception {
        importer.importFrom(fixtureDir());
        Long seoulId = regionId(regionService.getAllRegions(), "서울");
        contentService.updatePopularityScores(Map.of(126508L, 777));

        importer.importFrom(fixtureDir());

        assertThat(count("regions")).isEqualTo(6);
        assertThat(count("tour_classifications")).isEqualTo(12);
        assertThat(count("contents")).isEqualTo(4);
        assertThat(count("content_details")).isEqualTo(3);
        assertThat(count("category_mappings")).isEqualTo(
                jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT category_id, lcls_code) FROM category_mappings", Integer.class));
        assertThat(regionId(regionService.getAllRegions(), "서울")).isEqualTo(seoulId);
        assertThat(contentService.getActiveSnapshot(126508L).popularityScore()).isEqualTo(777);
        assertThat(batchJobLogService.findRecent(BatchJobName.TOUR_IMPORT)).hasSize(2);
    }

    private int count(String table) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
    }

    private Path fixtureDir() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(getClass().getResource("/tourapi")).toURI());
    }
}
