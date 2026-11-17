package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.category.service.CategorySeeder;
import com.jkymhs.backend.domain.content.ContentFixtures;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.entity.IoType;
import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static com.jkymhs.backend.domain.content.ContentFixtures.festival;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentServiceTest extends IntegrationTestSupport {

    @Autowired
    private ContentService contentService;

    @Autowired
    private RegionService regionService;

    @Autowired
    private TourClassificationService tourClassificationService;

    @Autowired
    private CategorySeeder categorySeeder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<RegionRef> regions;

    @BeforeEach
    void setUp() {
        regionService.upsertAll(ContentFixtures.REGIONS);
        tourClassificationService.upsertAll(ContentFixtures.CLASSIFICATIONS);
        categorySeeder.seed();
        regions = regionService.getAllRegions();
    }

    @Test
    @DisplayName("스냅샷에 지역 화면 이름·소분류 이름·카테고리·실내외·운영 정보가 들어간다")
    void snapshot() {
        load(attraction(1L, "경복궁", "11", "110"));
        contentService.applyIntro(1L, new TourIntro("09:00~18:00 (입장마감 17:00)", "매주 화요일", null, null, null,
                "3,000원", null, null, "02-3700-3900", null));

        ContentSnapshot snapshot = contentService.getActiveSnapshot(1L);

        assertThat(snapshot.contentType()).isEqualTo(ContentType.ATTRACTION);
        assertThat(snapshot.regionName()).isEqualTo("서울");
        assertThat(snapshot.districtName()).isEqualTo("종로구");
        assertThat(snapshot.regionId()).isEqualTo(ContentFixtures.regionId(regions, "서울 종로구"));
        assertThat(snapshot.categoryName()).isEqualTo("궁");
        assertThat(snapshot.ioType()).isEqualTo(IoType.OUTDOOR);
        assertThat(snapshot.openTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(snapshot.closeTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(snapshot.lastEntryTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(snapshot.restDays()).containsExactly(DayOfWeek.TUESDAY);
        assertThat(jdbcTemplate.queryForObject("SELECT category_id FROM contents WHERE id = 1", Long.class))
                .isEqualTo(jdbcTemplate.queryForObject("SELECT id FROM categories WHERE code = 'history'", Long.class));
        // 운영 정보로만 만든 상세 행은 상세(detailCommon2)를 받은 적이 없으므로 fetched_at이 비어 있다
        assertThat(jdbcTemplate.queryForMap("SELECT fee, fetched_at FROM content_details WHERE content_id = 1"))
                .containsEntry("fee", "3,000원").containsEntry("fetched_at", null);
    }

    @Test
    @DisplayName("세종처럼 시·군·구가 없으면 region_id는 시·도 id이고 districtName은 null이다")
    void sidoOnlyRegion() {
        load(attraction(2L, "세종호수공원", "36110", "36110"));

        ContentSnapshot snapshot = contentService.getActiveSnapshot(2L);

        Long sejong = ContentFixtures.regionId(regions, "세종");
        assertThat(snapshot.sidoRegionId()).isEqualTo(sejong);
        assertThat(snapshot.regionId()).isEqualTo(sejong);
        assertThat(snapshot.regionName()).isEqualTo("세종");
        assertThat(snapshot.districtName()).isNull();
    }

    @Test
    @DisplayName("삭제된 콘텐츠는 getActiveSnapshot이 404이고, findSnapshots에는 deleted=true로 들어간다")
    void deletedContent() {
        load(attraction(1L, "경복궁", "11", "110"));
        contentService.markDeleted(List.of(1L));

        assertThatThrownBy(() -> contentService.getActiveSnapshot(1L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("존재하지 않거나 삭제된 장소입니다.");
                });
        assertThatThrownBy(() -> contentService.getActiveSnapshot(999L)).isInstanceOf(CustomException.class);
        Map<Long, ContentSnapshot> snapshots = contentService.findSnapshots(List.of(1L, 999L));
        assertThat(snapshots).containsOnlyKeys(1L);
        assertThat(snapshots.get(1L).deleted()).isTrue();
    }

    @Test
    @DisplayName("후보 풀은 관광지 + 기간이 겹치는 축제이고, 시·도 id면 그 아래 시·군·구 콘텐츠까지 인기 점수순으로 준다")
    void candidatePool() {
        load(attraction(1L, "경복궁", "11", "110"),
                attraction(2L, "덕수궁", "11", "140"),
                attraction(3L, "해운대", "26", "350"),
                festival(10L, "끝나는 날 겹침", "11", "140", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5)),
                festival(11L, "시작하는 날 겹침", "11", "110", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)),
                festival(12L, "안 겹침", "11", "110", LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 20)),
                festival(13L, "이미 끝남", "11", "110", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 4)));
        contentService.updatePopularityScores(Map.of(2L, 900, 11L, 500, 1L, 100));
        LocalDate from = LocalDate.of(2026, 11, 5);
        LocalDate to = LocalDate.of(2026, 11, 10);

        List<ContentSnapshot> bySido = contentService.findCandidatePool(
                List.of(ContentFixtures.regionId(regions, "서울")), from, to, 10);
        List<ContentSnapshot> byDistrict = contentService.findCandidatePool(
                List.of(ContentFixtures.regionId(regions, "서울 종로구")), from, to, 10);

        assertThat(bySido).extracting(ContentSnapshot::id).containsExactly(2L, 11L, 1L, 10L);
        assertThat(byDistrict).extracting(ContentSnapshot::id).containsExactly(11L, 1L);
        assertThat(contentService.findCandidatePool(List.of(ContentFixtures.regionId(regions, "서울")), from, to, 2))
                .hasSize(2);
    }

    @Test
    @DisplayName("인기 점수를 한 번에 바꾸면 맵에 없는 콘텐츠는 0점이 된다")
    void popularityBulkUpdate() {
        load(attraction(1L, "경복궁", "11", "110"), attraction(2L, "덕수궁", "11", "140"));
        contentService.updatePopularityScores(Map.of(1L, 300, 2L, 700));

        contentService.updatePopularityScores(Map.of(2L, 1000));

        assertThat(contentService.findSnapshots(List.of(1L, 2L)))
                .extractingFromEntries(e -> e.getValue().popularityScore())
                .containsExactlyInAnyOrder(0, 1000);
    }

    @Test
    @DisplayName("찜 수는 더하고 빼되 0 아래로 내려가지 않는다")
    void wishCount() {
        load(attraction(1L, "경복궁", "11", "110"));

        contentService.adjustWishCount(1L, 2);
        contentService.adjustWishCount(1L, -1);
        assertThat(contentService.getActiveSnapshot(1L).wishCount()).isEqualTo(1);

        contentService.adjustWishCount(1L, -5);
        assertThat(contentService.getActiveSnapshot(1L).wishCount()).isZero();
    }

    @Test
    @DisplayName("다시 적재해도 인기 점수·찜 수·운영 정보는 남고, 삭제 표시는 풀린다")
    void upsertKeepsManagedColumns() {
        load(attraction(1L, "경복궁", "11", "110"));
        contentService.updatePopularityScores(Map.of(1L, 400));
        contentService.adjustWishCount(1L, 3);
        contentService.applyIntro(1L, new TourIntro("09:00~18:00", null, null, null, null, null, null, null, null, null));
        contentService.markDeleted(List.of(1L));

        load(attraction(1L, "경복궁(새 이름)", "11", "110"));

        ContentSnapshot snapshot = contentService.getActiveSnapshot(1L);
        assertThat(snapshot.name()).isEqualTo("경복궁(새 이름)");
        assertThat(snapshot.popularityScore()).isEqualTo(400);
        assertThat(snapshot.wishCount()).isEqualTo(3);
        assertThat(snapshot.openTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(snapshot.deleted()).isFalse();
    }

    @Test
    @DisplayName("상세를 반영하면 소개글·사진(지우고 다시)·반려동물·축제 공연 시간이 저장된다")
    void applyDetail() {
        load(festival(10L, "빛 축제", "11", "140", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));
        contentService.applyDetail(10L, new TourDetail("소개", "https://a.test", null, List.of("a.jpg", "b.jpg"), null));

        contentService.applyDetail(10L, new TourDetail("새 소개", null,
                new TourIntro(null, null, "18:00~22:00", null, "청계천", "무료", null, "서울시", null, "점등식"),
                List.of("c.jpg"), true));

        ContentSnapshot snapshot = contentService.getActiveSnapshot(10L);
        assertThat(snapshot.eventStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(snapshot.eventEndTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(snapshot.petAllowed()).isTrue();
        assertThat(jdbcTemplate.queryForList("SELECT image_url FROM content_images WHERE content_id = 10 ORDER BY sort_order",
                String.class)).containsExactly("c.jpg");
        assertThat(jdbcTemplate.queryForMap("SELECT overview, event_program FROM content_details WHERE content_id = 10"))
                .containsEntry("overview", "새 소개").containsEntry("event_program", "점등식");
        assertThat(jdbcTemplate.queryForObject("SELECT fetched_at FROM content_details WHERE content_id = 10",
                LocalDateTime.class)).isNotNull();
        assertThatThrownBy(() -> contentService.applyDetail(999L, new TourDetail(null, null, null, null, null)))
                .isInstanceOf(CustomException.class);
    }

    private void load(ContentSource... sources) {
        contentService.upsert(List.of(sources), contentService.loadContext());
    }
}
