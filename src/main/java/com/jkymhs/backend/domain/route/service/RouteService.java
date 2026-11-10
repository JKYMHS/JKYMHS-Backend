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

/**
 * 장소 사이 이동 수단·시간 계산. 여행 일정(D4)과 AI 추천(D5)이 같이 쓴다.
 *
 * 규칙 (기능 명세서 003-03, FE course-timeline):
 * <ol>
 *   <li>직선거리 ≤ 1000m(아이·부모님과 가면 500m) → 도보 (분당 67m). 외부 API를 부르지 않는다</li>
 *   <li>Redis 캐시(7일)에 있으면 그대로</li>
 *   <li>ODsay 대중교통과 카카오 택시를 동시에 부른다 (routeExecutor). 대중교통 시간 ≤ 1.5 × 택시면 대중교통(대안 택시),
 *       아니면 택시(대안 대중교통). 한쪽만 결과가 있으면 그쪽만</li>
 *   <li>둘 다 실패 → 직선거리 어림값 (1km 이하 도보 4km/h, 그 이상 자동차 30km/h), {@code estimated=true}, 캐시하지 않음
 *       (다음에 외부 API가 살아나면 제대로 계산하게)</li>
 * </ol>
 *
 * 도보 결과를 캐시하지 않는 이유: 같은 구간이라도 동행에 따라(500m/1000m) 도보 여부가 달라서, 좌표만으로 만든 키에 넣으면 섞인다.
 * Redis가 죽어도 경로 계산은 실패시키지 않는다 (캐시만 건너뛴다, G12).
 *
 * 구간 여러 개는 모든 외부 호출을 한꺼번에 실행기에 넣고 마지막에 모은다. 구간마다 "작업 안에서 또 작업을 기다리면"
 * 스레드가 모두 기다리는 쪽에 묶여서 멈출 수 있기 때문이다.
 */
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

    /**
     * @param companion 동행 FE 코드 ("kids"). null이면 일반 기준
     */
    public RouteResult route(LatLng from, LatLng to, String companion) {
        return routes(List.of(new Segment(from, to)), companion).getFirst();
    }

    /** 요청 순서대로 결과를 돌려준다 */
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

    /**
     * 외부 API 없이 답한다: 도보 거리면 도보, 캐시가 있으면 캐시, 없으면 직선거리 어림값.
     * 일정 저장(PUT)과 장소 담기처럼 사용자가 외부 API 응답을 기다리면 안 되는 곳에서 쓴다 (§5 Q12).
     */
    public RouteResult routeWithoutExternal(LatLng from, LatLng to, String companion) {
        return walkOrCached(from, to, companion).orElseGet(() -> estimate(from, to));
    }

    /** 직선거리 어림값 (외부 API 실패 시). 캐시하지 않는다 */
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

    /** 캐시에 있는 결과. Redis 장애면 빈 값 */
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
            // 형식이 깨진 캐시는 없는 것으로 본다 (다시 계산하면 덮어쓴다)
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
