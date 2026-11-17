package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.category.service.CategoryService;
import com.jkymhs.backend.domain.content.entity.Content;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.entity.IoType;
import com.jkymhs.backend.domain.content.entity.TourClassification;
import com.jkymhs.backend.domain.content.repository.ContentRepository;
import com.jkymhs.backend.domain.content.repository.TourClassificationRepository;
import com.jkymhs.backend.domain.region.service.RegionCodeIndex;
import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 콘텐츠 조회·적재. 다른 도메인(탐색, 찜, 여행, AI 추천, 인기 점수)은 contents 관련 테이블을 직접 읽거나 쓰지 않고 이 서비스만 쓴다.
 *
 * 쓰기는 JdbcTemplate로 한다. CSV 3만 건이나 동기화 1000건을 JPA로 한 건씩 저장하면 건마다 SELECT + INSERT/UPDATE가 나간다.
 * {@code INSERT … ON DUPLICATE KEY UPDATE}를 1000건씩 묶어(batchUpdate) 보내면 몇 번의 왕복으로 끝난다.
 * MySQL 8.0.19부터 쓸 수 있는 {@code VALUES (…) AS new} 별칭으로 새 값(new.col)과 기존 값(contents.col)을 구분한다. 함수 안에서는 테이블 이름을 붙여야 모호하지 않다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentService {

    static final String NOT_FOUND_MESSAGE = "존재하지 않거나 삭제된 장소입니다.";
    static final int BATCH_SIZE = 1000;

    /*
     * 다시 받아도 지우지 않는 값: 인기 점수·찜 수(다른 배치가 관리), 운영 정보(detailIntro2로 따로 받는다), 생성 시각.
     * 축제 날짜는 동기화 목록(areaBasedSyncList2)에 없을 수 있어서 새 값이 없으면 기존 값을 둔다.
     * 다시 받았다는 것은 TourAPI에 다시 보인다는 뜻이라 삭제 표시를 푼다.
     */
    private static final String UPSERT_CONTENT_SQL = """
            INSERT INTO contents (id, content_type, tour_content_type_id, name, address, latitude, longitude,
                sido_region_id, region_id, lcls1, lcls2, lcls3, category_id, io_type, thumbnail_url, tel,
                event_start_date, event_end_date, tour_modified_at, popularity_score, wish_count, is_deleted,
                created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?, ?) AS new
            ON DUPLICATE KEY UPDATE content_type = new.content_type, tour_content_type_id = new.tour_content_type_id,
                name = new.name, address = new.address, latitude = new.latitude, longitude = new.longitude,
                sido_region_id = new.sido_region_id, region_id = new.region_id, lcls1 = new.lcls1, lcls2 = new.lcls2,
                lcls3 = new.lcls3, category_id = new.category_id, io_type = new.io_type,
                thumbnail_url = new.thumbnail_url, tel = new.tel,
                event_start_date = COALESCE(new.event_start_date, contents.event_start_date),
                event_end_date = COALESCE(new.event_end_date, contents.event_end_date),
                tour_modified_at = new.tour_modified_at, is_deleted = 0, updated_at = new.updated_at
            """;

    private static final String FESTIVAL_INTRO_SQL = """
            UPDATE contents SET playtime_raw = ?, event_start_time = ?, event_end_time = ?, event_place = ?,
                intro_fetched_at = ?, updated_at = ?
            WHERE id = ?
            """;

    private static final String PLACE_INTRO_SQL = """
            UPDATE contents SET operating_hours_raw = ?, open_time = ?, close_time = ?, last_entry_time = ?,
                rest_date_raw = ?, rest_days = ?, intro_fetched_at = ?, updated_at = ?
            WHERE id = ?
            """;

    // 운영 정보에서 온 값만 고친다. 소개글·홈페이지와 fetched_at(상세 갱신 시각)은 그대로 둔다. 새 행이면 fetched_at은 NULL이다
    private static final String UPSERT_INTRO_DETAIL_SQL = """
            INSERT INTO content_details (content_id, fee, contact, host, age_limit, event_program)
            VALUES (?, ?, ?, ?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE fee = new.fee, contact = new.contact, host = new.host, age_limit = new.age_limit,
                event_program = new.event_program
            """;

    private static final String UPSERT_COMMON_DETAIL_SQL = """
            INSERT INTO content_details (content_id, overview, homepage, fetched_at)
            VALUES (?, ?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE overview = new.overview, homepage = new.homepage, fetched_at = new.fetched_at
            """;

    private final ContentRepository contentRepository;
    private final TourClassificationRepository tourClassificationRepository;
    private final RegionService regionService;
    private final CategoryService categoryService;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final Clock clock;

    // ---------- 조회 ----------

    /**
     * @throws CustomException 404 RESOURCE_NOT_FOUND "존재하지 않거나 삭제된 장소입니다."
     */
    public ContentSnapshot getActiveSnapshot(Long id) {
        Content content = (id == null ? Optional.<Content>empty() : contentRepository.findByIdAndDeletedFalse(id))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, NOT_FOUND_MESSAGE,
                        "존재하지 않거나 삭제된 콘텐츠: " + id));
        return toSnapshots(List.of(content)).getFirst();
    }

    /**
     * 삭제된 콘텐츠도 넣는다 (deleted=true). 찜·일정 화면이 "삭제된 장소"로 보여줘야 하기 때문이다. 없는 id는 빠진다.
     */
    public Map<Long, ContentSnapshot> findSnapshots(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Content> contents = contentRepository.findAllById(new HashSet<>(ids));
        return toSnapshots(contents).stream()
                .collect(Collectors.toMap(ContentSnapshot::id, s -> s, (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * AI 추천 후보 풀: 관광지 + 기간이 [from, to]와 겹치는 축제, 삭제 안 됨, 인기 점수 높은 순.
     *
     * @param regionIds 시·도 id가 섞여 있으면 그 시·도 전체(sido_region_id)로, 시·군·구 id는 region_id로 거른다
     */
    public List<ContentSnapshot> findCandidatePool(Collection<Long> regionIds, LocalDate from, LocalDate to, int limit) {
        if (regionIds == null || regionIds.isEmpty() || limit <= 0) {
            return List.of();
        }
        Set<Long> sidoIds = regionService.sidoIdsOf(regionIds);
        Set<Long> districtIds = regionIds.stream().filter(id -> !sidoIds.contains(id)).collect(Collectors.toSet());
        List<Content> contents = contentRepository.findCandidatePool(
                orNone(districtIds), orNone(sidoIds), from, to, PageRequest.of(0, limit));
        return toSnapshots(contents);
    }

    /** 지역 목록 API용 개수: 삭제 안 된 관광지 전부 + 끝나지 않은(진행 중·예정) 축제. */
    public List<RegionContentCount> countActiveByRegion(LocalDate today) {
        return jdbcTemplate.query("""
                        SELECT sido_region_id, region_id, content_type, COUNT(*) AS cnt FROM contents
                        WHERE is_deleted = 0 AND sido_region_id IS NOT NULL
                          AND (content_type = 'ATTRACTION' OR (content_type = 'FESTIVAL' AND event_end_date >= ?))
                        GROUP BY sido_region_id, region_id, content_type
                        """,
                (rs, rowNum) -> new RegionContentCount(rs.getLong("sido_region_id"),
                        (Long) rs.getObject("region_id", Long.class),
                        ContentType.valueOf(rs.getString("content_type")), rs.getInt("cnt")),
                today);
    }

    public boolean hasAnyContent() {
        return contentRepository.count() > 0;
    }

    public Optional<LocalDateTime> findMaxTourModifiedAt() {
        return contentRepository.findMaxTourModifiedAt();
    }

    /** 운영 정보를 한 번도 안 받았거나 {@code before}보다 오래된 관광지·축제, 오래된 순. */
    public List<IntroTarget> findIntroTargets(int limit, LocalDateTime before) {
        if (limit <= 0) {
            return List.of();
        }
        return contentRepository.findIntroTargets(List.of(ContentType.ATTRACTION, ContentType.FESTIVAL), before,
                        PageRequest.of(0, limit)).stream()
                .map(c -> new IntroTarget(c.getId(), c.getTourContentTypeId()))
                .toList();
    }

    /** 이미 저장된 축제 기간. 동기화 목록(areaBasedSyncList2)에는 축제 기간이 없어서, 이미 있는 축제는 저장된 기간을 그대로 쓴다. */
    public Map<Long, EventPeriod> findFestivalPeriods(Collection<Long> ids) {
        Map<Long, EventPeriod> periods = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return periods;
        }
        for (List<Long> chunk : chunks(new ArrayList<>(new HashSet<>(ids)))) {
            namedJdbcTemplate.query("""
                            SELECT id, event_start_date, event_end_date FROM contents
                            WHERE id IN (:ids) AND content_type = 'FESTIVAL'
                              AND event_start_date IS NOT NULL AND event_end_date IS NOT NULL
                            """,
                    new MapSqlParameterSource("ids", chunk),
                    rs -> {
                        periods.put(rs.getLong("id"), new EventPeriod(rs.getObject("event_start_date", LocalDate.class),
                                rs.getObject("event_end_date", LocalDate.class)));
                    });
        }
        return periods;
    }

    public Map<Long, ContentType> findTypes(Collection<Long> ids) {
        Map<Long, ContentType> types = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return types;
        }
        for (List<Long> chunk : chunks(new ArrayList<>(new HashSet<>(ids)))) {
            namedJdbcTemplate.query("SELECT id, content_type FROM contents WHERE id IN (:ids)",
                    new MapSqlParameterSource("ids", chunk),
                    rs -> {
                        types.put(rs.getLong("id"), ContentType.valueOf(rs.getString("content_type")));
                    });
        }
        return types;
    }

    // ---------- 적재 ----------

    /** 콘텐츠 적재 전에 한 번 만든다 (지역·카테고리·실내외 변환표). */
    public ContentLoadContext loadContext() {
        RegionCodeIndex regions = regionService.codeIndex();
        Map<String, IoType> ioTypes = new HashMap<>();
        for (TourClassification classification : tourClassificationRepository.findAll()) {
            if (classification.getIoType() != null) {
                ioTypes.put(classification.getCode(), classification.getIoType());
            }
        }
        return new ContentLoadContext(regions, categoryService.matcher(), ioTypes);
    }

    /**
     * 콘텐츠를 id 기준으로 넣거나 고친다 (1000건씩 묶어서). {@link ContentSource#skipReason()}이 있는 항목은 건너뛴다.
     *
     * @return 넣거나 고친 건수 (건너뛴 항목 제외)
     */
    @Transactional
    public int upsert(List<ContentSource> sources, ContentLoadContext context) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Object[]> rows = new ArrayList<>();
        for (ContentSource source : sources) {
            if (source.skipReason() != null) {
                continue;
            }
            RegionCodeIndex.Resolved region = context.regions().resolve(source.ldongRegnCd(), source.ldongSignguCd());
            Long categoryId = context.categories().categoryIdFor(source.contentType() == ContentType.FESTIVAL,
                    source.lcls1(), source.lcls2(), source.lcls3());
            IoType ioType = source.lcls3() == null ? null : context.ioTypes().get(source.lcls3());
            rows.add(new Object[]{
                    source.id(), source.contentType().name(), source.tourContentTypeId(), cut(source.name(), 200),
                    cut(source.address(), 255), source.latitude(), source.longitude(),
                    region == null ? null : region.sidoRegionId(), region == null ? null : region.regionId(),
                    cut(source.lcls1(), 5), cut(source.lcls2(), 6), cut(source.lcls3(), 10), categoryId,
                    ioType == null ? null : ioType.name(), cut(source.thumbnailUrl(), 500), cut(source.tel(), 100),
                    source.eventStartDate(), source.eventEndDate(), source.tourModifiedAt(), now, now
            });
        }
        for (List<Object[]> chunk : chunks(rows)) {
            jdbcTemplate.batchUpdate(UPSERT_CONTENT_SQL, chunk);
        }
        return rows.size();
    }

    /**
     * TourAPI에서 내려간 콘텐츠(showflag=0)를 삭제 표시한다. 행은 지우지 않는다 (찜·일정이 참조한다).
     *
     * @return 새로 삭제 표시한 건수
     */
    @Transactional
    public int markDeleted(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int updated = 0;
        for (List<Long> chunk : chunks(new ArrayList<>(new HashSet<>(ids)))) {
            updated += namedJdbcTemplate.update(
                    "UPDATE contents SET is_deleted = 1, updated_at = :now WHERE id IN (:ids) AND is_deleted = 0",
                    new MapSqlParameterSource("ids", chunk).addValue("now", now));
        }
        return updated;
    }

    /**
     * 운영 정보 원문을 시각·요일로 바꿔서 contents 컬럼에 넣고, 요금·문의처 등은 content_details에 넣는다.
     * 축제는 공연 시간(playtime) → event_start_time/event_end_time, 그 밖은 운영 시간(usetime) → open/close/last_entry와 쉬는 요일이다.
     *
     * @return 콘텐츠가 없어서 고치지 못했으면 false
     */
    @Transactional
    public boolean applyIntro(Long contentId, TourIntro intro) {
        ContentType type = findTypes(List.of(contentId)).get(contentId);
        if (type == null || intro == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (type == ContentType.FESTIVAL) {
            String playTime = firstText(intro.playTime(), intro.useTime());
            OperatingHours hours = OperatingHoursParser.parse(playTime);
            LocalTime start = hours.open() != null ? hours.open() : OperatingHoursParser.firstTime(intro.eventStartTime());
            jdbcTemplate.update(FESTIVAL_INTRO_SQL, cut(playTime, 500), start, hours.close(),
                    cut(blankToNull(intro.eventPlace()), 255), now, now, contentId);
        } else {
            OperatingHours hours = OperatingHoursParser.parse(intro.useTime());
            jdbcTemplate.update(PLACE_INTRO_SQL, cut(blankToNull(intro.useTime()), 500), hours.open(), hours.close(),
                    hours.lastEntry(), cut(blankToNull(intro.restDate()), 500),
                    RestDays.toColumn(RestDayParser.parse(intro.restDate())), now, now, contentId);
        }
        String fee = cut(blankToNull(intro.fee()), 500);
        String contact = cut(blankToNull(intro.contact()), 100);
        String host = cut(blankToNull(intro.host()), 255);
        String ageLimit = cut(blankToNull(intro.ageLimit()), 100);
        String program = blankToNull(intro.program());
        if (fee != null || contact != null || host != null || ageLimit != null || program != null) {
            jdbcTemplate.update(UPSERT_INTRO_DETAIL_SQL, contentId, fee, contact, host, ageLimit, program);
        }
        return true;
    }

    /**
     * 상세 갱신(D1) 결과를 저장한다: 소개글·홈페이지(content_details), 운영 정보, 사진(지우고 다시 넣기), 반려동물 동반 여부.
     * null로 온 부분(그 호출이 실패함)은 저장된 값을 그대로 둔다.
     *
     * @throws CustomException 404 RESOURCE_NOT_FOUND — 콘텐츠 없음
     */
    @Transactional
    public void applyDetail(Long id, TourDetail detail) {
        if (findTypes(List.of(id)).isEmpty()) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, NOT_FOUND_MESSAGE, "존재하지 않는 콘텐츠: " + id);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        jdbcTemplate.update(UPSERT_COMMON_DETAIL_SQL, id, blankToNull(detail.overview()),
                cut(blankToNull(detail.homepage()), 500), now);
        if (detail.intro() != null) {
            applyIntro(id, detail.intro());
        }
        if (detail.imageUrls() != null) {
            jdbcTemplate.update("DELETE FROM content_images WHERE content_id = ?", id);
            List<Object[]> rows = new ArrayList<>();
            for (String url : detail.imageUrls()) {
                if (url != null && !url.isBlank() && url.length() <= 500) {
                    rows.add(new Object[]{id, url, rows.size() + 1});
                }
            }
            jdbcTemplate.batchUpdate("INSERT INTO content_images (content_id, image_url, sort_order) VALUES (?, ?, ?)", rows);
        }
        if (detail.petAllowed() != null) {
            jdbcTemplate.update("UPDATE contents SET pet_allowed = ?, updated_at = ? WHERE id = ?",
                    detail.petAllowed(), now, id);
        }
    }

    /**
     * 인기 점수 배치(D3) 결과를 한 번에 반영한다. 맵에 없는 콘텐츠는 0점이 된다.
     * 먼저 0이 아닌 행을 0으로 돌리고, 맵의 점수를 1000건씩 묶어서 쓴다 (한 트랜잭션이라 중간 상태가 조회되지 않는다).
     */
    @Transactional
    public void updatePopularityScores(Map<Long, Integer> scores) {
        jdbcTemplate.update("UPDATE contents SET popularity_score = 0 WHERE popularity_score <> 0");
        List<Object[]> rows = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() != 0)
                .map(e -> new Object[]{e.getValue(), e.getKey()})
                .toList();
        for (List<Object[]> chunk : chunks(rows)) {
            jdbcTemplate.batchUpdate("UPDATE contents SET popularity_score = ? WHERE id = ?", chunk);
        }
    }

    /**
     * 찜 수를 원자적으로 더하거나 뺀다 (읽고 더해서 쓰면 동시에 찜할 때 하나가 사라진다). 0 아래로는 내려가지 않는다.
     */
    @Transactional
    public void adjustWishCount(Long id, int delta) {
        jdbcTemplate.update("UPDATE contents SET wish_count = GREATEST(wish_count + ?, 0) WHERE id = ?", delta, id);
    }

    // ---------- 내부 ----------

    private List<ContentSnapshot> toSnapshots(List<Content> contents) {
        if (contents.isEmpty()) {
            return List.of();
        }
        Set<Long> regionIds = new HashSet<>();
        Set<String> lcls3Codes = new HashSet<>();
        for (Content content : contents) {
            if (content.getSidoRegionId() != null) {
                regionIds.add(content.getSidoRegionId());
            }
            if (content.getRegionId() != null) {
                regionIds.add(content.getRegionId());
            }
            if (content.getLcls3() != null) {
                lcls3Codes.add(content.getLcls3());
            }
        }
        Map<Long, RegionRef> regions = regionService.getRegions(regionIds);
        Map<String, String> classificationNames = tourClassificationRepository.findAllById(lcls3Codes).stream()
                .collect(Collectors.toMap(TourClassification::getCode, TourClassification::getName));
        return contents.stream().map(c -> toSnapshot(c, regions, classificationNames)).toList();
    }

    private static ContentSnapshot toSnapshot(Content c, Map<Long, RegionRef> regions, Map<String, String> classificationNames) {
        RegionRef sido = c.getSidoRegionId() == null ? null : regions.get(c.getSidoRegionId());
        RegionRef district = c.getRegionId() == null || Objects.equals(c.getRegionId(), c.getSidoRegionId())
                ? null : regions.get(c.getRegionId());
        return new ContentSnapshot(
                c.getId(), c.getContentType(), c.getName(), c.getAddress(),
                c.getLatitude().doubleValue(), c.getLongitude().doubleValue(), c.getThumbnailUrl(),
                c.getSidoRegionId(), c.getRegionId(),
                sido == null ? null : sido.name(), district == null ? null : district.name(),
                c.getLcls1(), c.getLcls2(), c.getLcls3(),
                c.getLcls3() == null ? null : classificationNames.get(c.getLcls3()), c.getIoType(),
                c.getEventStartDate(), c.getEventEndDate(), c.getEventStartTime(), c.getEventEndTime(),
                c.getOpenTime(), c.getCloseTime(), c.getLastEntryTime(), RestDays.fromColumn(c.getRestDays()),
                c.getPetAllowed(), c.getPopularityScore(), c.getWishCount(), c.isDeleted());
    }

    // JPQL "in ()"를 피하려고 빈 목록 대신 존재할 수 없는 id 하나를 넣는다
    private static Collection<Long> orNone(Collection<Long> ids) {
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    private static <T> List<List<T>> chunks(List<T> rows) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            chunks.add(rows.subList(i, Math.min(i + BATCH_SIZE, rows.size())));
        }
        return chunks;
    }

    private static String firstText(String first, String second) {
        String value = blankToNull(first);
        return value != null ? value : blankToNull(second);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // 컬럼 길이를 넘는 값 하나 때문에 1000건 묶음 전체가 실패하지 않게 자른다
    static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
