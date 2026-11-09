package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentNearbyService;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.recommendation.dto.response.CourseResponse;
import com.jkymhs.backend.domain.route.service.LatLng;
import com.jkymhs.backend.domain.route.service.RouteResult;
import com.jkymhs.backend.domain.route.service.RouteService;
import com.jkymhs.backend.domain.route.service.Segment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 코스 계획에 이동(4단계 ROUTES)과 시각·식사 칸(5단계 MEALS)을 붙여 화면에 낼 코스로 만든다.
 *
 * 경로: 코스 3개의 모든 구간(그날 출발점 → 첫 장소, 장소 → 다음 장소)을 모아 중복을 빼고 {@link RouteService#routes} 한 번으로
 * 계산한다. 둘째 날부터의 출발점은 전날 마지막 장소다. 제한 시간이 지났으면 외부 API 없이 캐시·어림값만 쓴다.
 * 식사 칸의 주변 음식점 수는 바로 앞 장소(없으면 그날 출발점) 1km 안의 음식점 수다.
 */
@Component
@RequiredArgsConstructor
public class CourseAssembler {

    static final int NEARBY_RESTAURANT_RADIUS_METERS = 1000;

    private final RouteService routeService;
    private final ContentNearbyService contentNearbyService;

    public List<CourseResponse> assemble(List<PlannedCourse> plans, CourseContext context, String companion,
                                         boolean allowExternalRoutes) {
        Map<Segment, RouteResult> routes = routes(plans, context, companion, allowExternalRoutes);
        Map<LatLng, Integer> nearbyCounts = new HashMap<>();
        List<CourseResponse> courses = new ArrayList<>();
        for (int c = 0; c < plans.size(); c++) {
            courses.add(course("c" + (c + 1), plans.get(c), context, routes, nearbyCounts));
        }
        return courses;
    }

    private Map<Segment, RouteResult> routes(List<PlannedCourse> plans, CourseContext context, String companion,
                                             boolean allowExternal) {
        Map<Segment, RouteResult> routes = new LinkedHashMap<>();
        for (PlannedCourse plan : plans) {
            forEachSegment(plan, context, segment -> routes.put(segment, null));
        }
        List<Segment> segments = new ArrayList<>(routes.keySet());
        if (allowExternal) {
            List<RouteResult> results = routeService.routes(segments, companion);
            for (int i = 0; i < segments.size(); i++) {
                routes.put(segments.get(i), results.get(i));
            }
        } else {
            segments.forEach(s -> routes.put(s, routeService.routeWithoutExternal(s.from(), s.to(), companion)));
        }
        return routes;
    }

    private static void forEachSegment(PlannedCourse plan, CourseContext context,
                                       java.util.function.Consumer<Segment> consumer) {
        LatLng from = context.origin();
        for (PlannedCourse.Day day : plan.days()) {
            for (PlannedCourse.Place place : day.places()) {
                LatLng to = latLng(place.candidate().content());
                consumer.accept(new Segment(from, to));
                from = to;
            }
        }
    }

    private CourseResponse course(String courseId, PlannedCourse plan, CourseContext context,
                                  Map<Segment, RouteResult> routes, Map<LatLng, Integer> nearbyCounts) {
        List<CourseResponse.Day> days = new ArrayList<>();
        CourseResponse.Origin dayOrigin = new CourseResponse.Origin(context.originName(), context.origin().lat(),
                context.origin().lng());
        int placeCount = 0;
        int moveMinutes = 0;
        for (PlannedCourse.Day day : plan.days()) {
            List<PlannedCourse.Place> places = day.places();
            List<RouteResult> moves = new ArrayList<>();
            LatLng from = new LatLng(dayOrigin.lat(), dayOrigin.lng());
            for (PlannedCourse.Place place : places) {
                LatLng to = latLng(place.candidate().content());
                moves.add(routes.get(new Segment(from, to)));
                from = to;
            }
            List<CourseTimeline.Slot> slots = CourseTimeline.layout(context.dayStart(day.dayNumber()),
                    places.stream().map(PlannedCourse.Place::stayMinutes).toList(),
                    moves.stream().map(RouteResult::minutes).toList());

            List<CourseResponse.TimelineItem> items = new ArrayList<>();
            LatLng lastPoint = new LatLng(dayOrigin.lat(), dayOrigin.lng());
            for (CourseTimeline.Slot slot : slots) {
                if (slot instanceof CourseTimeline.PlaceSlot placeSlot) {
                    PlannedCourse.Place place = places.get(placeSlot.index());
                    RouteResult move = moves.get(placeSlot.index());
                    items.add(CourseResponse.MoveItem.from(move));
                    items.add(placeItem(place, placeSlot));
                    lastPoint = latLng(place.candidate().content());
                    moveMinutes += move.minutes();
                    placeCount++;
                } else if (slot instanceof CourseTimeline.MealSlot mealSlot) {
                    items.add(new CourseResponse.MealItem(mealSlot.mealType(), CourseTimeline.toTime(mealSlot.start()),
                            CourseTimeline.toTime(mealSlot.end()), nearbyCount(lastPoint, nearbyCounts)));
                }
            }
            days.add(new CourseResponse.Day(day.dayNumber(), day.date(), dayOrigin, items));
            if (!places.isEmpty()) {
                ContentSnapshot last = places.getLast().candidate().content();
                dayOrigin = new CourseResponse.Origin(last.name(), last.lat(), last.lng());
            }
        }
        String title = plan.title() == null || plan.title().isBlank() ? FallbackCourseBuilder.TITLE : plan.title();
        return new CourseResponse(courseId, title, new CourseResponse.Summary(placeCount, moveMinutes), days);
    }

    private static CourseResponse.PlaceItem placeItem(PlannedCourse.Place place, CourseTimeline.PlaceSlot slot) {
        ContentSnapshot content = place.candidate().content();
        return new CourseResponse.PlaceItem(content.id(), content.name(), content.categoryName(), content.ioType(),
                content.thumbnailUrl(), content.lat(), content.lng(), CourseTimeline.toTime(slot.start()),
                CourseTimeline.toTime(slot.end()), place.stayMinutes(), place.reason(), place.candidate().required());
    }

    private int nearbyCount(LatLng point, Map<LatLng, Integer> cache) {
        return cache.computeIfAbsent(point, p -> contentNearbyService.countWithin(ContentType.RESTAURANT, p.lat(),
                p.lng(), NEARBY_RESTAURANT_RADIUS_METERS));
    }

    private static LatLng latLng(ContentSnapshot content) {
        return new LatLng(content.lat(), content.lng());
    }
}
