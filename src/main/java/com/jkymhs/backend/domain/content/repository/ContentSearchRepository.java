package com.jkymhs.backend.domain.content.repository;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentSort;
import com.jkymhs.backend.domain.content.service.FestivalStatus;
import com.jkymhs.backend.domain.content.service.RestDays;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 탐색 화면 조회 SQL. 조건 조합이 많아서(탭·검색어·지역·카테고리·상태·칩·지도 범위) JPA 파생 쿼리로는 메서드가 수십 개가 된다.
 * QueryDSL은 이 프로젝트에 없어서, 조건마다 WHERE 조각을 붙이는 작은 빌더({@link Where})와 NamedParameterJdbcTemplate을 쓴다.
 * 값은 모두 이름 붙은 파라미터(:name)로 넘기므로 사용자 입력이 SQL 문자열에 들어가지 않는다.
 *
 * 목록은 id·정렬 값만 읽는다. 화면에 필요한 나머지(지역 이름, 소분류 이름 등)는 {@code ContentService.findSnapshots}가 채운다.
 */
@Repository
@RequiredArgsConstructor
public class ContentSearchRepository {

    // 반경 → 위도·경도 차이 변환: 위도 1도 ≈ 111.32km. 경도 1도는 위도가 높을수록 짧아진다 (× cos(위도))
    private static final double METERS_PER_DEGREE = 111_320d;
    private static final String DISTANCE_SQL = "ST_Distance_Sphere(POINT(longitude, latitude), POINT(:lng, :lat))";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /** 목록 한 페이지에 쓰는 정렬 값 (커서를 만든다). */
    public record ListRow(long id, LocalDate startDate, int popularity, String name) {
    }

    /** 커서 위치: 이 항목 "다음"부터 읽는다. 정렬에 쓰지 않는 값은 null */
    public record ListCursor(LocalDate startDate, Integer popularity, String name, long id) {
    }

    public record AutocompleteRow(long id, int rank, int popularity) {
    }

    public record DistanceRow(long id, double distanceMeters) {
    }

    /**
     * 키셋(keyset) 페이지네이션: "마지막으로 본 (정렬 값, id)보다 뒤"를 WHERE로 거르고 LIMIT으로 자른다.
     * OFFSET은 앞 페이지 행을 모두 읽고 버려서 뒤로 갈수록 느려지고, 그 사이 행이 추가·삭제되면 중복·누락이 생긴다.
     *
     * @param after 첫 페이지면 null
     * @param limit 보통 size + 1 (다음 페이지가 있는지 보려고 하나 더 읽는다)
     */
    public List<ListRow> findPage(ContentSearchCondition condition, ContentSort sort, ListCursor after, int limit) {
        Where where = filters(condition);
        if (after != null) {
            where.and(keysetClause(sort), p -> {
                p.addValue("cId", after.id());
                p.addValue("cName", after.name());
                p.addValue("cDate", after.startDate());
                p.addValue("cPop", after.popularity());
            });
        }
        String sql = "SELECT id, event_start_date, popularity_score, name FROM contents" + where.sql()
                + " ORDER BY " + orderBy(sort) + " LIMIT :limit";
        return jdbcTemplate.query(sql, where.params().addValue("limit", limit),
                (rs, rowNum) -> new ListRow(rs.getLong("id"), rs.getObject("event_start_date", LocalDate.class),
                        rs.getInt("popularity_score"), rs.getString("name")));
    }

    public int count(ContentSearchCondition condition) {
        Where where = filters(condition);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contents" + where.sql(), where.params(),
                Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * 자동완성: 이름에 검색어가 들어간 콘텐츠를 순위대로. 순위 = 고른 지역 안 → 이름이 검색어로 시작 → 인기 점수 → id.
     * rank는 지역(2)·앞부분 일치(1) 점수를 더한 값이라 종류가 다른 목록(장소·축제)을 섞을 때도 같은 기준으로 비교할 수 있다.
     *
     * @param sidoRegionId     고른 지역이 시·도면 그 id (아니면 null)
     * @param regionId         고른 지역이 시·군·구면 그 id (아니면 null)
     * @param festivalEndingOn 축제는 이 날짜 이후에 끝나는 것만 (끝난 축제 제외). 장소면 null
     */
    public List<AutocompleteRow> autocomplete(ContentType type, String keyword, Long sidoRegionId, Long regionId,
                                              LocalDate festivalEndingOn, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("type", type.name())
                .addValue("contains", "%" + keyword + "%")
                .addValue("prefix", keyword + "%")
                .addValue("limit", limit);
        String regionMatch = "0";
        if (sidoRegionId != null) {
            regionMatch = "(sido_region_id = :regionId)";
            params.addValue("regionId", sidoRegionId);
        } else if (regionId != null) {
            regionMatch = "(region_id = :regionId)";
            params.addValue("regionId", regionId);
        }
        String festivalClause = "";
        if (festivalEndingOn != null) {
            festivalClause = " AND event_end_date >= :today";
            params.addValue("today", festivalEndingOn);
        }
        String sql = """
                SELECT id, popularity_score, (CASE WHEN %s THEN 2 ELSE 0 END) + (CASE WHEN name LIKE :prefix THEN 1 ELSE 0 END) AS match_rank
                FROM contents
                WHERE content_type = :type AND is_deleted = 0 AND name LIKE :contains%s
                ORDER BY match_rank DESC, popularity_score DESC, id
                LIMIT :limit
                """.formatted(regionMatch, festivalClause);
        return jdbcTemplate.query(sql, params,
                (rs, rowNum) -> new AutocompleteRow(rs.getLong("id"), rs.getInt("match_rank"), rs.getInt("popularity_score")));
    }

    /**
     * 기준점에서 반경 안의 콘텐츠.
     * 먼저 위도·경도 사각형(BETWEEN)으로 좁혀서 (content_type, latitude, longitude) 인덱스를 타고,
     * 그 안에서만 실제 거리(ST_Distance_Sphere, 지구를 구로 본 미터 거리)를 계산한다. 거리 계산만으로 거르면 전체 행을 계산하게 된다.
     *
     * @param festivalEndingOn FESTIVAL이 types에 있으면 이 날짜 이후에 끝나는 축제만 (null이면 거르지 않음)
     * @param excludeId        빼야 할 콘텐츠 (상세 화면의 자기 자신). null이면 없음
     * @param popularFirst     true면 인기 점수 높은 순 → 가까운 순, false면 가까운 순 → 인기 점수 높은 순
     */
    public List<DistanceRow> findWithin(Collection<ContentType> types, double lat, double lng, int radiusMeters,
                                        LocalDate festivalEndingOn, Long excludeId, boolean popularFirst, int limit) {
        Where where = within(types, lat, lng, radiusMeters, festivalEndingOn, excludeId);
        String order = popularFirst ? "popularity_score DESC, distance, id" : "distance, popularity_score DESC, id";
        String sql = "SELECT id, " + DISTANCE_SQL + " AS distance FROM contents" + where.sql()
                + " ORDER BY " + order + " LIMIT :limit";
        return jdbcTemplate.query(sql, where.params().addValue("limit", limit),
                (rs, rowNum) -> new DistanceRow(rs.getLong("id"), rs.getDouble("distance")));
    }

    public int countWithin(Collection<ContentType> types, double lat, double lng, int radiusMeters) {
        Where where = within(types, lat, lng, radiusMeters, null, null);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contents" + where.sql(), where.params(),
                Integer.class);
        return count == null ? 0 : count;
    }

    // ---------- 조건 ----------

    private static Where filters(ContentSearchCondition c) {
        Where where = new Where();
        where.and("content_type = :type AND is_deleted = 0", p -> p.addValue("type", c.contentType().name()));
        if (c.keyword() != null) {
            where.and("(name LIKE :keyword OR address LIKE :keyword)", p -> p.addValue("keyword", "%" + c.keyword() + "%"));
        }
        if (c.sidoRegionId() != null) {
            where.and("sido_region_id = :sidoRegionId", p -> p.addValue("sidoRegionId", c.sidoRegionId()));
        }
        if (c.regionId() != null) {
            where.and("region_id = :regionId", p -> p.addValue("regionId", c.regionId()));
        }
        if (c.categoryId() != null) {
            where.and("category_id = :categoryId", p -> p.addValue("categoryId", c.categoryId()));
        }
        if (c.statuses() != null && !c.statuses().isEmpty()) {
            where.and(statusClause(c.statuses()), p -> p.addValue("today", c.today()));
        }
        if (c.overlapFrom() != null && c.overlapTo() != null) {
            // 기간 겹침: 시작 ≤ 구간 끝 그리고 끝 ≥ 구간 시작
            where.and("event_start_date <= :overlapTo AND event_end_date >= :overlapFrom", p -> {
                p.addValue("overlapFrom", c.overlapFrom());
                p.addValue("overlapTo", c.overlapTo());
            });
        }
        if (c.minPopularity() != null) {
            where.and("popularity_score >= :minPopularity", p -> p.addValue("minPopularity", c.minPopularity()));
        }
        if (c.openDay() != null && c.openAt() != null) {
            // 쉬는 날 컬럼은 "MON,TUE" 형식이라 FIND_IN_SET으로 요일을 찾는다 ("NONE"·NULL이면 0)
            // 밤을 넘기는 영업(18:00~02:00, close ≤ open)은 "open 이후이거나 close 이전"이면 영업 중이다
            where.and("""
                    (rest_days IS NULL OR FIND_IN_SET(:openDay, rest_days) = 0)
                    AND open_time IS NOT NULL AND close_time IS NOT NULL
                    AND ((open_time < close_time AND open_time <= :openAt AND :openAt < close_time)
                         OR (close_time <= open_time AND (:openAt >= open_time OR :openAt < close_time)))""", p -> {
                p.addValue("openDay", RestDays.shortName(c.openDay()));
                p.addValue("openAt", c.openAt());
            });
        }
        if (c.lclsCodes() != null && !c.lclsCodes().isEmpty()) {
            where.and("(lcls1 IN (:lcls) OR lcls2 IN (:lcls) OR lcls3 IN (:lcls))",
                    p -> p.addValue("lcls", List.copyOf(c.lclsCodes())));
        }
        if (c.night()) {
            // 축제는 행사 시각, 장소는 닫는 시각으로 본다. 닫는 시각이 여는 시각보다 이르면 자정을 넘겨 영업하는 곳이다
            String clause = c.contentType() == ContentType.FESTIVAL
                    ? "(event_end_time >= :nightFrom OR event_start_time >= :nightFrom)"
                    : "(close_time > :nightFrom OR close_time < open_time)";
            where.and(clause, p -> p.addValue("nightFrom", LocalTime.of(20, 0)));
        }
        if (c.bbox() != null) {
            where.and("latitude BETWEEN :swLat AND :neLat AND longitude BETWEEN :swLng AND :neLng", p -> {
                p.addValue("swLat", c.bbox().swLat());
                p.addValue("neLat", c.bbox().neLat());
                p.addValue("swLng", c.bbox().swLng());
                p.addValue("neLng", c.bbox().neLng());
            });
        }
        return where;
    }

    private static String statusClause(Collection<FestivalStatus> statuses) {
        return statuses.stream().sorted().map(status -> switch (status) {
            case ONGOING -> "(event_start_date <= :today AND event_end_date >= :today)";
            case UPCOMING -> "(event_start_date > :today)";
            case ENDED -> "(event_end_date < :today)";
        }).collect(Collectors.joining(" OR ", "(", ")"));
    }

    private static Where within(Collection<ContentType> types, double lat, double lng, int radiusMeters,
                                LocalDate festivalEndingOn, Long excludeId) {
        double latDelta = radiusMeters / METERS_PER_DEGREE;
        // 극지방에서 cos가 0에 가까워져 사각형이 무한히 넓어지지 않게 하한을 둔다
        double lngDelta = radiusMeters / (METERS_PER_DEGREE * Math.max(Math.cos(Math.toRadians(lat)), 0.01));
        Where where = new Where();
        where.and("content_type IN (:types) AND is_deleted = 0", p -> p.addValue("types",
                types.stream().map(ContentType::name).toList()));
        where.and("latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLng AND :maxLng", p -> {
            p.addValue("minLat", lat - latDelta);
            p.addValue("maxLat", lat + latDelta);
            p.addValue("minLng", lng - lngDelta);
            p.addValue("maxLng", lng + lngDelta);
        });
        where.and(DISTANCE_SQL + " <= :radius", p -> {
            p.addValue("lat", lat);
            p.addValue("lng", lng);
            p.addValue("radius", radiusMeters);
        });
        if (festivalEndingOn != null) {
            where.and("(content_type <> 'FESTIVAL' OR event_end_date >= :today)", p -> p.addValue("today", festivalEndingOn));
        }
        if (excludeId != null) {
            where.and("id <> :excludeId", p -> p.addValue("excludeId", excludeId));
        }
        return where;
    }

    // 정렬과 커서 조건은 짝이 맞아야 한다: ORDER BY a ASC, b ASC, id ASC면 "(a, b, id) > (커서 값)"을 풀어 쓴 조건이다
    private static String orderBy(ContentSort sort) {
        return switch (sort) {
            case LATEST -> "event_start_date, name, id";
            case POPULAR -> "popularity_score DESC, name, id";
            case NAME -> "name, id";
        };
    }

    private static String keysetClause(ContentSort sort) {
        String nameThenId = "(name > :cName OR (name = :cName AND id > :cId))";
        return switch (sort) {
            case LATEST -> "(event_start_date > :cDate OR (event_start_date = :cDate AND " + nameThenId + "))";
            case POPULAR -> "(popularity_score < :cPop OR (popularity_score = :cPop AND " + nameThenId + "))";
            case NAME -> nameThenId;
        };
    }

    /** WHERE 조각과 파라미터를 같이 모은다. 조건이 하나도 없으면 빈 문자열이다. */
    private static final class Where {

        private final List<String> clauses = new ArrayList<>();
        private final MapSqlParameterSource params = new MapSqlParameterSource();

        void and(String clause, Consumer<MapSqlParameterSource> binder) {
            clauses.add(clause);
            binder.accept(params);
        }

        String sql() {
            return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        }

        MapSqlParameterSource params() {
            return params;
        }
    }
}
