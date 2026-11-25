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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentService {

    static final String NOT_FOUND_MESSAGE = "존재하지 않거나 삭제된 장소입니다.";
    static final int BATCH_SIZE = 1000;

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

    public ContentSnapshot getActiveSnapshot(Long id) {
        Content content = (id == null ? Optional.<Content>empty() : contentRepository.findByIdAndDeletedFalse(id))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, NOT_FOUND_MESSAGE,
                        "존재하지 않거나 삭제된 콘텐츠: " + id));
        return toSnapshots(List.of(content)).getFirst();
    }

    public Map<Long, ContentSnapshot> findSnapshots(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Content> contents = contentRepository.findAllById(new HashSet<>(ids));
        return toSnapshots(contents).stream()
                .collect(Collectors.toMap(ContentSnapshot::id, s -> s, (a, b) -> a, LinkedHashMap::new));
    }

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

    public List<IntroTarget> findIntroTargets(int limit, LocalDateTime before) {
        if (limit <= 0) {
            return List.of();
        }
        return contentRepository.findIntroTargets(List.of(ContentType.ATTRACTION, ContentType.FESTIVAL), before,
                        PageRequest.of(0, limit)).stream()
                .map(c -> new IntroTarget(c.getId(), c.getTourContentTypeId()))
                .toList();
    }

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

    @Transactional
    public void adjustWishCount(Long id, int delta) {
        jdbcTemplate.update("UPDATE contents SET wish_count = GREATEST(wish_count + ?, 0) WHERE id = ?", delta, id);
    }

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
