package com.jkymhs.backend.domain.route.service;

import com.jkymhs.backend.domain.route.client.kakaomobility.CarRoute;
import com.jkymhs.backend.domain.route.client.kakaomobility.KakaoMobilityClient;
import com.jkymhs.backend.domain.route.client.odsay.OdsayClient;
import com.jkymhs.backend.domain.route.client.odsay.TransitRoute;
import com.jkymhs.backend.global.model.Companion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

@Slf4j
@Service
public class RouteService {
    static final double WALK_METERS_PER_MINUTE = 67.0;
    private static final double ESTIMATE_WALK_METERS_PER_MINUTE = 4000.0 / 60;
    private static final double ESTIMATE_CAR_METERS_PER_MINUTE = 30000.0 / 60;
    private static final int ESTIMATE_WALK_LIMIT_METERS = 1000;
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private OdsayClient odsayClient;
    private KakaoMobilityClient kakaoMobilityClient;
    private final StringRedisTemplate redisTemplate;
    private final RouteProperties properties;
    private final TaskExecutor routeExecutor;

    public RouteService(OdsayClient odsayClient, KakaoMobilityClient kakaoMobilityClient,
                        StringRedisTemplate redisTemplate, RouteProperties properties,
                        @Qualifier("routeExecutor") TaskExecutor routeExecutor) {
        this.odsayClient = odsayClient;
        this.kakaoMobilityClient = kakaoMobilityClient;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.routeExecutor = routeExecutor;
    }

    public RouteResult route(LatLng from, LatLng to, String companion) {
        return routes(List.of(new Segment(from, to)), companion).getFirst();
    }

    public List<RouteResult> routes(List<Segment> segments, String companion) {
        RouteResult[] results = new RouteResult[segments.size()];
        Map<Integer, CompletableFuture<Optional<TransitRoute>>> transits = new HashMap<>();
        Map<Integer, CompletableFuture<Optional<CarRoute>>> cars = new HashMap<>();

        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            Optional<RouteResult> offline = walkOrCached(segment.from(), segment.to(), companion);
            if (offline.isPresent()) {
                results[i] = offline.get();
                continue;
            }
            transits.put(i, submit(() -> odsayClient.search(segment.from(), segment.to())));
            cars.put(i, submit(() -> kakaoMobilityClient.directions(segment.from(), segment.to())));
        }

        for (Integer i : transits.keySet()) {
            Segment segment = segments.get(i);
            Optional<TransitRoute> transit = join(transits.get(i));
            Optional<CarRoute> car = join(cars.get(i));
            RouteResult result = combine(segment, transit, car);
            if (result == null) {
                results[i] = estimate(segment.from(), segment.to());
            } else {
                results[i] = result;
                writeCache(segment, result);
            }
        }
        return new ArrayList<>(List.of(results));
    }

    public RouteResult routeWithoutExternal(LatLng from, LatLng to, String companion) {
        return walkOrCached(from, to, companion).orElseGet(() -> estimate(from, to));
    }

    public RouteResult estimate(LatLng from, LatLng to) {
        int distance = distanceMeters(from, to);
        String polyline = PolylineEncoder.straight(from, to);
        if (distance <= ESTIMATE_WALK_LIMIT_METERS) {
            return new RouteResult(MoveMode.WALK, minutes(distance, ESTIMATE_WALK_METERS_PER_MINUTE), null, null, null,
                    polyline, true, distance);
        }
        return new RouteResult(MoveMode.TAXI, minutes(distance, ESTIMATE_CAR_METERS_PER_MINUTE), null, null, null,
                polyline, true, distance);
    }

    public Optional<RouteResult> cached(LatLng from, LatLng to) {
        try {
            Map<Object, Object> hash = redisTemplate.opsForHash().entries(RouteRedisKeys.route(from, to));
            if (hash == null || hash.isEmpty()) {
                return Optional.empty();
            }
            String altMode = (String) hash.get("altMode");
            String altMinutes = (String) hash.get("altMinutes");
            String summary = (String) hash.get("summary");
            return Optional.of(new RouteResult(
                    MoveMode.valueOf((String) hash.get("mode")),
                    Integer.parseInt((String) hash.get("minutes")),
                    isBlank(altMode) ? null : MoveMode.valueOf(altMode),
                    isBlank(altMinutes) ? null : Integer.valueOf(altMinutes),
                    isBlank(summary) ? null : summary,
                    (String) hash.get("polyline"),
                    false,
                    Integer.parseInt((String) hash.get("distance"))));
        } catch (DataAccessException e) {
            log.warn("경로 캐시 조회 실패 (캐시 없이 계산): {}", e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("경로 캐시 형식 오류: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public static int distanceMeters(LatLng from, LatLng to) {
        double dLat = Math.toRadians(to.lat() - from.lat());
        double dLng = Math.toRadians(to.lng() - from.lng());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(from.lat())) * Math.cos(Math.toRadians(to.lat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return (int) Math.round(2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private Optional<RouteResult> walkOrCached(LatLng from, LatLng to, String companion) {
        int distance = distanceMeters(from, to);
        int threshold = isSlowWalker(companion) ? properties.slowWalkThresholdMeters() : properties.walkThresholdMeters();
        if (distance <= threshold) {
            return Optional.of(new RouteResult(MoveMode.WALK, minutes(distance, WALK_METERS_PER_MINUTE), null, null,
                    null, PolylineEncoder.straight(from, to), false, distance));
        }
        return cached(from, to);
    }

    private RouteResult combine(Segment segment, Optional<TransitRoute> transit, Optional<CarRoute> car) {
        String straight = PolylineEncoder.straight(segment.from(), segment.to());
        int straightDistance = distanceMeters(segment.from(), segment.to());
        if (transit.isPresent() && car.isPresent()) {
            TransitRoute t = transit.get();
            CarRoute c = car.get();
            if (t.minutes() <= properties.transitRatio() * c.minutes()) {
                return new RouteResult(MoveMode.TRANSIT, t.minutes(), MoveMode.TAXI, c.minutes(), t.summary(), straight,
                        false, t.distanceMeters() > 0 ? t.distanceMeters() : straightDistance);
            }
            return new RouteResult(MoveMode.TAXI, c.minutes(), MoveMode.TRANSIT, t.minutes(), null, carPolyline(c, straight),
                    false, c.distanceMeters());
        }
        if (car.isPresent()) {
            CarRoute c = car.get();
            return new RouteResult(MoveMode.TAXI, c.minutes(), null, null, null, carPolyline(c, straight), false,
                    c.distanceMeters());
        }
        if (transit.isPresent()) {
            TransitRoute t = transit.get();
            return new RouteResult(MoveMode.TRANSIT, t.minutes(), null, null, t.summary(), straight, false,
                    t.distanceMeters() > 0 ? t.distanceMeters() : straightDistance);
        }
        return null;
    }

    private static String carPolyline(CarRoute car, String fallback) {
        return car.path().size() >= 2 ? PolylineEncoder.encode(car.path()) : fallback;
    }

    private void writeCache(Segment segment, RouteResult result) {
        String key = RouteRedisKeys.route(segment.from(), segment.to());
        Map<String, String> hash = new HashMap<>();
        hash.put("mode", result.mode().name());
        hash.put("minutes", String.valueOf(result.minutes()));
        hash.put("altMode", result.altMode() == null ? "" : result.altMode().name());
        hash.put("altMinutes", result.altMinutes() == null ? "" : String.valueOf(result.altMinutes()));
        hash.put("summary", result.summary() == null ? "" : result.summary());
        hash.put("polyline", result.polyline());
        hash.put("distance", String.valueOf(result.distanceMeters()));
        try {
            redisTemplate.opsForHash().putAll(key, hash);
            redisTemplate.expire(key, properties.cacheTtl());
        } catch (DataAccessException e) {
            log.warn("경로 캐시 저장 실패 (무시): {}", e.getMessage());
        }
    }

    private <T> CompletableFuture<Optional<T>> submit(Supplier<Optional<T>> call) {
        try {
            return CompletableFuture.supplyAsync(call, routeExecutor);
        } catch (RejectedExecutionException e) {
            log.warn("경로 실행기가 가득 참 (어림값으로 대신함)");
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static <T> Optional<T> join(CompletableFuture<Optional<T>> future) {
        try {
            return future.join();
        } catch (RuntimeException e) {
            log.warn("경로 외부 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isSlowWalker(String companion) {
        if (companion == null) {
            return false;
        }
        for (Companion value : Companion.values()) {
            if (value.code().equalsIgnoreCase(companion) || value.name().equalsIgnoreCase(companion)) {
                return value.slowWalker();
            }
        }
        return false;
    }

    private static int minutes(int distance, double metersPerMinute) {
        return Math.max(1, (int) Math.ceil(distance / metersPerMinute));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
