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

@Repository
@RequiredArgsConstructor
public class ContentSearchRepository {

    private static final double METERS_PER_DEGREE = 111_320d;
    private static final String DISTANCE_SQL = "ST_Distance_Sphere(POINT(longitude, latitude), POINT(:lng, :lat))";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public record ListRow(long id, LocalDate startDate, int popularity, String name) {
    }

    public record ListCursor(LocalDate startDate, Integer popularity, String name, long id) {
    }

    public record AutocompleteRow(long id, int rank, int popularity) {
    }

    public record DistanceRow(long id, double distanceMeters) {
    }

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
            where.and("event_start_date <= :overlapTo AND event_end_date >= :overlapFrom", p -> {
                p.addValue("overlapFrom", c.overlapFrom());
                p.addValue("overlapTo", c.overlapTo());
            });
        }
        if (c.minPopularity() != null) {
            where.and("popularity_score >= :minPopularity", p -> p.addValue("minPopularity", c.minPopularity()));
        }
        if (c.openDay() != null && c.openAt() != null) {
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
