package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.category.entity.CategoryTab;
import com.jkymhs.backend.domain.category.service.CategoryRef;
import com.jkymhs.backend.domain.category.service.CategoryService;
import com.jkymhs.backend.domain.content.dto.request.ContentListRequest;
import com.jkymhs.backend.domain.content.dto.response.ContentItemResponse;
import com.jkymhs.backend.domain.content.dto.response.ContentListResponse;
import com.jkymhs.backend.domain.content.dto.response.ContentLookupResponse;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.repository.ContentSearchCondition;
import com.jkymhs.backend.domain.content.repository.ContentSearchCondition.Bbox;
import com.jkymhs.backend.domain.content.repository.ContentSearchRepository;
import com.jkymhs.backend.domain.content.repository.ContentSearchRepository.ListCursor;
import com.jkymhs.backend.domain.content.repository.ContentSearchRepository.ListRow;
import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.global.pagination.CursorCodec;
import com.jkymhs.backend.global.pagination.CursorPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 탐색 목록(GET /api/contents)과 비교 담기 조회(GET /api/contents/lookup).
 *
 * 요청 값 검증 → 조회 조건(ContentSearchCondition) 만들기 → 키셋 페이지 조회 → 카드로 바꾸기 순서다.
 * 조회 조건을 SQL로 바꾸는 일은 {@link ContentSearchRepository}가 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentExploreService {

    static final String INVALID_FILTER_MESSAGE = "잘못된 필터 값입니다.";
    private static final int DEFAULT_SIZE = 20;
    private static final int POPULAR_SCORE_THRESHOLD = 800;
    private static final Set<FestivalStatus> DEFAULT_FESTIVAL_STATUSES =
            EnumSet.of(FestivalStatus.ONGOING, FestivalStatus.UPCOMING);

    // 커서 JSON 키: t 탭, s 정렬, k 정렬 값 목록, i 마지막 id, v 인기 점수 버전 (짧게 써서 URL을 줄인다)
    private static final String KEY_TAB = "t";
    private static final String KEY_SORT = "s";
    private static final String KEY_VALUES = "k";
    private static final String KEY_ID = "i";
    private static final String KEY_VERSION = "v";

    private final ContentSearchRepository contentSearchRepository;
    private final ContentService contentService;
    private final RegionService regionService;
    private final CategoryService categoryService;
    private final ContentItemAssembler itemAssembler;
    private final CursorCodec cursorCodec;
    private final StringRedisTemplate redisTemplate;
    private final ExploreProperties exploreProperties;
    private final Clock clock;

    /**
     * @throws CustomException 400 INVALID_INPUT (필터 값), 400 INVALID_CURSOR, 409 CURSOR_VERSION_MISMATCH
     */
    public ContentListResponse getList(ContentListRequest request, Long userId) {
        CategoryTab tab = request.tab();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        int size = request.size() == null ? DEFAULT_SIZE : request.size();
        if (size > exploreProperties.pageSizeMax()) {
            throw invalidFilter("size 최대값 초과: " + size);
        }
        ContentSort sort = request.sort() == null ? ContentSort.defaultOf(tab) : request.sort();
        if (!sort.allowedIn(tab)) {
            throw invalidFilter(tab.code() + " 탭에서 쓸 수 없는 정렬: " + sort.code());
        }
        ContentChip chip = request.chip() == null || request.chip() == ContentChip.ALL ? null : request.chip();
        if (chip != null && !chip.allowedIn(tab)) {
            throw invalidFilter(tab.code() + " 탭에서 쓸 수 없는 칩: " + chip.code());
        }
        if (tab == CategoryTab.PLACE && request.status() != null && !request.status().isEmpty()) {
            throw invalidFilter("관광지 탭에는 진행 상태 필터가 없음");
        }
        String keyword = SearchKeyword.optional(request.q());
        RegionRef region = request.regionId() == null ? null : regionService.getRegion(request.regionId());
        Long categoryId = resolveCategory(request.categoryId(), tab);
        Bbox bbox = parseBbox(request.bbox());

        // 인기순 커서에 넣을 인기 점수 버전. 첫 페이지는 지금 버전, 다음 페이지는 받은 커서의 버전을 그대로 이어 쓴다
        Long version = null;
        ListCursor after = null;
        if (request.cursor() != null && !request.cursor().isBlank()) {
            Map<String, Object> cursor = cursorCodec.decode(request.cursor());
            after = readCursor(cursor, tab, sort);
            if (sort == ContentSort.POPULAR) {
                version = CursorCodec.requireLong(cursor, KEY_VERSION);
                checkVersion(version);
            }
        } else if (sort == ContentSort.POPULAR) {
            // Redis를 못 읽으면 0을 넣는다. 다음 페이지에서도 못 읽으면 비교를 건너뛴다 (캐시 장애로 목록이 막히지 않게)
            version = popularityVersion().orElse(0L);
        }

        ContentSearchCondition condition = condition(tab, chip, keyword, region, categoryId, request.status(),
                bbox, now);
        List<ListRow> rows = contentSearchRepository.findPage(condition, sort, after, size + 1);
        Long cursorVersion = version;
        CursorPage<ListRow> page = CursorPage.of(rows, size, row -> encodeCursor(tab, sort, row, cursorVersion));
        List<ContentItemResponse> items = itemAssembler.toItems(page.items().stream().map(ListRow::id).toList(), userId);

        ContentListResponse.TabCounts counts = after == null ? countTabs(keyword, region, bbox, today) : null;
        return new ContentListResponse(items, page.nextCursor(), page.hasNext(), counts, today);
    }

    /**
     * 비교 담기: 보낸 순서를 지키고, 같은 id를 여러 번 보내면 한 번만 준다.
     */
    public ContentLookupResponse lookup(List<Long> ids, Long userId) {
        List<Long> unique = new ArrayList<>(new LinkedHashSet<>(ids));
        Map<Long, ContentSnapshot> snapshots = contentService.findSnapshots(unique);
        List<ContentItemResponse> items = itemAssembler.toItems(unique, snapshots, userId);
        List<Long> missing = unique.stream()
                .filter(id -> snapshots.get(id) == null || snapshots.get(id).deleted())
                .toList();
        return new ContentLookupResponse(items, missing);
    }

    // ---------- 조건 ----------

    private ContentSearchCondition condition(CategoryTab tab, ContentChip chip, String keyword, RegionRef region,
                                             Long categoryId, List<FestivalStatus> requestedStatuses, Bbox bbox,
                                             LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        boolean festival = tab == CategoryTab.FESTIVAL;
        Set<FestivalStatus> statuses = null;
        if (festival) {
            statuses = requestedStatuses == null || requestedStatuses.isEmpty()
                    ? DEFAULT_FESTIVAL_STATUSES : EnumSet.copyOf(requestedStatuses);
            if (chip == ContentChip.ONGOING) {
                // "지금 진행중" 칩은 상태 필터보다 우선한다
                statuses = EnumSet.of(FestivalStatus.ONGOING);
            }
        }
        LocalDate overlapFrom = null;
        LocalDate overlapTo = null;
        if (chip == ContentChip.WEEKEND) {
            LocalDate[] weekend = comingWeekend(today);
            overlapFrom = weekend[0];
            overlapTo = weekend[1];
        }
        boolean openNow = chip == ContentChip.OPEN_NOW;
        return new ContentSearchCondition(
                festival ? ContentType.FESTIVAL : ContentType.ATTRACTION,
                keyword,
                region != null && region.isSido() ? region.id() : null,
                region != null && !region.isSido() ? region.id() : null,
                categoryId,
                statuses,
                today,
                overlapFrom,
                overlapTo,
                chip == ContentChip.POPULAR800 ? POPULAR_SCORE_THRESHOLD : null,
                openNow ? today.getDayOfWeek() : null,
                openNow ? now.toLocalTime() : null,
                chip != null && chip.isClassification() ? categoryService.lclsCodesOf(chip.code()) : null,
                chip == ContentChip.NIGHT,
                bbox);
    }

    /**
     * 이번 주말 [토, 일]. 오늘이 토요일이면 [오늘, 내일], 일요일이면 [오늘]만 (지난 토요일은 이미 지났다).
     */
    static LocalDate[] comingWeekend(LocalDate today) {
        return switch (today.getDayOfWeek()) {
            case SATURDAY -> new LocalDate[]{today, today.plusDays(1)};
            case SUNDAY -> new LocalDate[]{today, today};
            default -> {
                LocalDate saturday = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
                yield new LocalDate[]{saturday, saturday.plusDays(1)};
            }
        };
    }

    private Long resolveCategory(Long categoryId, CategoryTab tab) {
        if (categoryId == null) {
            return null;
        }
        CategoryRef category = categoryService.getCategory(categoryId);
        if (category.groupType() != tab.groupType()) {
            throw invalidFilter("탭에 속하지 않는 카테고리: " + categoryId);
        }
        return category.id();
    }

    static Bbox parseBbox(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 4) {
            throw invalidFilter("bbox 값 개수가 4개가 아님");
        }
        double[] values = new double[4];
        try {
            for (int i = 0; i < 4; i++) {
                values[i] = Double.parseDouble(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            throw invalidFilter("bbox 숫자 형식 오류");
        }
        double swLat = values[0];
        double swLng = values[1];
        double neLat = values[2];
        double neLng = values[3];
        boolean inRange = swLat >= -90 && neLat <= 90 && swLng >= -180 && neLng <= 180;
        if (!inRange || swLat > neLat || swLng > neLng) {
            throw invalidFilter("bbox 범위 오류");
        }
        return new Bbox(swLat, swLng, neLat, neLng);
    }

    private ContentListResponse.TabCounts countTabs(String keyword, RegionRef region, Bbox bbox, LocalDate today) {
        Long sidoId = region != null && region.isSido() ? region.id() : null;
        Long districtId = region != null && !region.isSido() ? region.id() : null;
        int festivals = contentSearchRepository.count(new ContentSearchCondition(ContentType.FESTIVAL, keyword, sidoId,
                districtId, null, DEFAULT_FESTIVAL_STATUSES, today, null, null, null, null, null, null, false, bbox));
        int places = contentSearchRepository.count(new ContentSearchCondition(ContentType.ATTRACTION, keyword, sidoId,
                districtId, null, null, today, null, null, null, null, null, null, false, bbox));
        return new ContentListResponse.TabCounts(festivals, places);
    }

    // ---------- 커서 ----------

    private String encodeCursor(CategoryTab tab, ContentSort sort, ListRow row, Long version) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(KEY_TAB, tab.code());
        values.put(KEY_SORT, sort.code());
        values.put(KEY_VALUES, switch (sort) {
            case LATEST -> List.of(row.startDate().toString(), row.name());
            case POPULAR -> List.of(row.popularity(), row.name());
            case NAME -> List.of(row.name());
        });
        values.put(KEY_ID, row.id());
        if (sort == ContentSort.POPULAR) {
            values.put(KEY_VERSION, version == null ? 0L : version);
        }
        return cursorCodec.encode(values);
    }

    /**
     * 다른 탭·정렬에서 만든 커서는 400이다 (정렬 값의 뜻이 달라서 이상한 위치부터 보여주게 된다).
     * 사용자가 커서를 고쳐서 타입이 틀린 경우도 500이 아니라 400 INVALID_CURSOR가 되게 하나씩 확인한다.
     */
    private static ListCursor readCursor(Map<String, Object> cursor, CategoryTab tab, ContentSort sort) {
        CursorCodec.requireSame(cursor, KEY_TAB, tab.code());
        CursorCodec.requireSame(cursor, KEY_SORT, sort.code());
        long id = CursorCodec.requireLong(cursor, KEY_ID);
        if (!(cursor.get(KEY_VALUES) instanceof List<?> values)) {
            throw invalidCursor("정렬 값 없음");
        }
        int expectedSize = sort == ContentSort.NAME ? 1 : 2;
        if (values.size() != expectedSize || !(values.get(expectedSize - 1) instanceof String name)) {
            throw invalidCursor("정렬 값 형식 오류");
        }
        return switch (sort) {
            case LATEST -> {
                if (!(values.get(0) instanceof String date)) {
                    throw invalidCursor("시작일 형식 오류");
                }
                try {
                    yield new ListCursor(LocalDate.parse(date), null, name, id);
                } catch (DateTimeParseException e) {
                    throw invalidCursor("시작일 형식 오류");
                }
            }
            case POPULAR -> {
                if (!(values.get(0) instanceof Number popularity)) {
                    throw invalidCursor("인기 점수 형식 오류");
                }
                yield new ListCursor(null, popularity.intValue(), name, id);
            }
            case NAME -> new ListCursor(null, null, name, id);
        };
    }

    /**
     * 인기 점수 배치(1시간마다)가 점수를 다시 매기면 버전이 오른다. 그 전에 받은 커서로 이어 읽으면
     * 점수가 바뀐 항목이 빠지거나 두 번 나와서 409로 처음부터 다시 받게 한다.
     */
    private void checkVersion(long cursorVersion) {
        OptionalLong current = popularityVersion();
        if (current.isPresent() && current.getAsLong() != cursorVersion) {
            throw new CustomException(ErrorCode.CURSOR_VERSION_MISMATCH,
                    "인기 점수 버전 변경: " + cursorVersion + " → " + current.getAsLong());
        }
    }

    /** Redis를 못 읽으면 비어 있다. 값이 없으면 0 (배치가 아직 한 번도 안 돌았다) */
    private OptionalLong popularityVersion() {
        try {
            String value = redisTemplate.opsForValue().get(ContentRedisKeys.popularityVersion());
            return OptionalLong.of(value == null ? 0L : Long.parseLong(value.trim()));
        } catch (DataAccessException e) {
            log.warn("popularity version unavailable: {}", e.getMessage());
            return OptionalLong.empty();
        } catch (NumberFormatException e) {
            return OptionalLong.of(0L);
        }
    }

    private static CustomException invalidFilter(String description) {
        return new CustomException(ErrorCode.INVALID_INPUT, INVALID_FILTER_MESSAGE, description);
    }

    private static CustomException invalidCursor(String description) {
        return new CustomException(ErrorCode.INVALID_CURSOR, description);
    }
}
