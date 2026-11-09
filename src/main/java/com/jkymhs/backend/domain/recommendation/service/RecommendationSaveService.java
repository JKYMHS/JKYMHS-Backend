package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.recommendation.dto.request.RecommendationSaveRequest;
import com.jkymhs.backend.domain.recommendation.dto.response.CourseResponse;
import com.jkymhs.backend.domain.recommendation.dto.response.RecommendationSaveResponse;
import com.jkymhs.backend.domain.recommendation.entity.MealType;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequest;
import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequestRepository;
import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.domain.route.service.LatLng;
import com.jkymhs.backend.domain.route.service.RouteResult;
import com.jkymhs.backend.domain.route.service.RouteService;
import com.jkymhs.backend.domain.route.service.Segment;
import com.jkymhs.backend.domain.trip.entity.TripOrigin;
import com.jkymhs.backend.domain.trip.service.NewTripCommand;
import com.jkymhs.backend.domain.trip.service.PlanDraft;
import com.jkymhs.backend.domain.trip.service.PlanItemDraft;
import com.jkymhs.backend.domain.trip.service.TripPlanIds;
import com.jkymhs.backend.domain.trip.service.TripService;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 추천 코스를 여행/일정안으로 저장한다 (004-07). 일정안 만들기 자체는 여행 도메인({@link TripService})이 하고,
 * 여기서는 Redis의 코스를 {@link PlanDraft}로 바꾼다.
 *
 * <ul>
 *   <li>장소 칸은 그대로, 고른 식사 칸은 그 자리·그 시각의 음식점 항목이 된다. 고르지 않은 식사 칸과 사용자가 뺀 장소는 저장하지 않는다.</li>
 *   <li>이동은 결과에 있던 값을 쓰되, 앞 항목이 바뀐 항목(식사 뒤, 뺀 장소 뒤)만 {@link RouteService#routes}로 다시 계산한다.</li>
 *   <li>{tripId} → 기존 여행에 일정안 추가 (확정 여부 선택), {newTrip} → 추천 조건으로 채운 새 여행 + 확정안.</li>
 *   <li>사용자가 뺀 장소는 취향 신호(AI_REMOVE, −3)로 보낸다. 일정 담기 신호(SCHEDULE_SAVE)는 여행 도메인이 보낸다.</li>
 * </ul>
 * 한 트랜잭션이라 여행 도메인에서 422/403이 나면 아무것도 남지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationSaveService {

    static final String NOT_FOUND_MESSAGE = "추천 결과가 만료되었습니다. 다시 추천받아주세요.";
    private static final DateTimeFormatter PLAN_NAME_DATE = DateTimeFormatter.ofPattern("MM.dd");
    private static final int MAX_TRIP_REGIONS = 5;

    private final RecommendationRequestRepository requestRepository;
    private final RecommendationService recommendationService;
    private final RecommendationResultStore resultStore;
    private final TripService tripService;
    private final ContentService contentService;
    private final RegionService regionService;
    private final RouteService routeService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * @throws CustomException 400 INVALID_INPUT(대상 둘 다·둘 다 없음, 음식점이 아님, 없는 식사 칸), 403/404 여행,
     *                         404 결과 만료·없는 courseId, 422 PLAN_EXCEEDS_TRIP_PERIOD
     */
    @Transactional
    public RecommendationSaveResponse save(Long userId, Long requestId, RecommendationSaveRequest body) {
        RecommendationSaveRequest.Target target = body.target();
        if ((target.tripId() == null) == (target.newTrip() == null)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "저장할 여행을 하나만 선택해주세요.", "tripId·newTrip 중 하나만");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        RecommendationRequest request = requestRepository.findByIdAndUserId(requestId, userId)
                .filter(r -> !r.isExpired(now) && r.getStatus().hasResult())
                .orElseThrow(() -> notFound("요청 없음·만료·결과 없음: " + requestId));
        CourseResponse course = resultStore.findResult(requestId)
                .flatMap(result -> result.course(body.courseId()))
                .orElseThrow(() -> notFound("결과 또는 코스 없음: " + body.courseId()));

        String companion = target.newTrip() != null && target.newTrip().companion() != null
                ? target.newTrip().companion().code() : request.getCompanion().code();
        List<PlanItemDraft> items = items(course, body, companion);
        String planName = body.planName() != null ? body.planName().trim()
                : "AI 추천 · " + request.getStartDate().format(PLAN_NAME_DATE);
        PlanDraft draft = new PlanDraft(planName, request.getSource(), request.getId(), items);

        TripPlanIds ids;
        boolean confirmed;
        if (target.tripId() != null) {
            ids = tripService.addPlan(userId, target.tripId(), draft, body.confirm());
            confirmed = body.confirm();
        } else {
            ids = tripService.createTripWithPlan(userId, newTrip(request, target.newTrip()), draft);
            confirmed = true;
        }
        publishRemoved(userId, course, body.removedIds(), ids.tripId(), companion, now);
        return new RecommendationSaveResponse(ids.tripId(), ids.planId(), confirmed);
    }

    // ---------- 코스 → 일정안 초안 ----------

    private List<PlanItemDraft> items(CourseResponse course, RecommendationSaveRequest body, String companion) {
        Set<Long> removed = new HashSet<>(body.removedIds());
        Map<String, Long> meals = mealSelections(course, body);
        Map<Long, ContentSnapshot> restaurants = restaurants(meals.values());

        List<Entry> entries = new ArrayList<>();
        LatLng origin = null;
        for (CourseResponse.Day day : course.days()) {
            if (origin == null) {
                origin = new LatLng(day.origin().lat(), day.origin().lng());
            }
            Object originalPrevious = day.dayNumber() == 1 ? "origin" : null;
            Object finalPrevious = originalPrevious;
            CourseResponse.MoveItem pendingMove = null;
            int sortOrder = 0;
            for (CourseResponse.TimelineItem item : day.items()) {
                if (item instanceof CourseResponse.MoveItem move) {
                    pendingMove = move;
                } else if (item instanceof CourseResponse.PlaceItem place) {
                    if (!removed.contains(place.contentId())) {
                        // 결과의 이동은 "결과에서 바로 앞 칸"에서 온 값이라, 저장본의 앞 항목이 같을 때만 그대로 쓴다
                        boolean keepMove = pendingMove != null && Objects.equals(finalPrevious, originalPrevious);
                        entries.add(new Entry(day.dayNumber(), ++sortOrder, place.contentId(), place.lat(), place.lng(),
                                place.startTime(), place.endTime(), place.stayMinutes(), place.reason(),
                                place.isRequired(), keepMove ? pendingMove.toRoute() : null));
                        finalPrevious = place.contentId();
                    }
                    originalPrevious = place.contentId();
                    pendingMove = null;
                } else if (item instanceof CourseResponse.MealItem meal) {
                    Long restaurantId = meals.get(mealKey(day.dayNumber(), meal.mealType()));
                    if (restaurantId != null) {
                        ContentSnapshot restaurant = restaurants.get(restaurantId);
                        entries.add(new Entry(day.dayNumber(), ++sortOrder, restaurantId, restaurant.lat(),
                                restaurant.lng(), meal.startTime(), meal.endTime(), MealType.MINUTES, null, false, null));
                        finalPrevious = "meal:" + restaurantId;
                    }
                }
            }
        }
        fillMoves(entries, origin, companion);
        return entries.stream().map(Entry::toDraft).toList();
    }

    /*
     * 다시 계산할 구간을 모아서 한 번에 부른다. 그날 첫 항목: 첫날은 출발지에서 오고, 둘째 날부터는 이동이 없다
     * (여행 도메인이 전날 숙소를 모르므로 그날 첫 항목의 이동은 비운다).
     */
    private void fillMoves(List<Entry> entries, LatLng origin, String companion) {
        List<Segment> segments = new ArrayList<>();
        List<Entry> targets = new ArrayList<>();
        Entry previous = null;
        for (Entry entry : entries) {
            boolean firstOfDay = previous == null || previous.dayNumber != entry.dayNumber;
            LatLng from = null;
            if (!firstOfDay) {
                from = new LatLng(previous.lat, previous.lng);
            } else if (entry.dayNumber == 1 && origin != null) {
                from = origin;
            }
            if (from == null) {
                entry.move = null;
            } else if (entry.move == null) {
                segments.add(new Segment(from, new LatLng(entry.lat, entry.lng)));
                targets.add(entry);
            }
            previous = entry;
        }
        if (segments.isEmpty()) {
            return;
        }
        List<RouteResult> routes = routeService.routes(segments, companion);
        for (int i = 0; i < targets.size(); i++) {
            targets.get(i).move = routes.get(i);
        }
    }

    // (날짜, 식사) → 음식점 id. 코스에 없는 식사 칸을 고르면 400
    private static Map<String, Long> mealSelections(CourseResponse course, RecommendationSaveRequest body) {
        Set<String> slots = new HashSet<>();
        for (CourseResponse.Day day : course.days()) {
            for (CourseResponse.TimelineItem item : day.items()) {
                if (item instanceof CourseResponse.MealItem meal) {
                    slots.add(mealKey(day.dayNumber(), meal.mealType()));
                }
            }
        }
        Map<String, Long> selections = new HashMap<>();
        for (RecommendationSaveRequest.MealSelection selection : body.meals()) {
            String key = mealKey(selection.dayNumber(), selection.mealType());
            if (!slots.contains(key)) {
                throw new CustomException(ErrorCode.INVALID_INPUT, "선택한 식사 시간이 코스에 없습니다.", "식사 칸 없음: " + key);
            }
            selections.put(key, selection.contentId());
        }
        return selections;
    }

    private Map<Long, ContentSnapshot> restaurants(Collection<Long> ids) {
        Map<Long, ContentSnapshot> found = contentService.findSnapshots(new LinkedHashSet<>(ids));
        for (Long id : ids) {
            ContentSnapshot content = found.get(id);
            if (content == null || content.deleted() || content.contentType() != ContentType.RESTAURANT) {
                throw new CustomException(ErrorCode.INVALID_INPUT, "식사 시간에는 음식점만 넣을 수 있습니다.",
                        "음식점이 아니거나 없는 콘텐츠: " + id);
            }
        }
        return found;
    }

    private static String mealKey(int dayNumber, MealType mealType) {
        return dayNumber + ":" + mealType.name();
    }

    // ---------- 새 여행 ----------

    private NewTripCommand newTrip(RecommendationRequest request, RecommendationSaveRequest.NewTrip newTrip) {
        List<Long> districtIds = recommendationService.districtIdsOf(request.getId());
        RegionRef sido = regionService.getRegions(List.of(request.getSidoRegionId())).get(request.getSidoRegionId());
        String regionName = sido == null ? "AI 추천" : sido.name();
        var startDate = newTrip.startDate() != null ? newTrip.startDate() : request.getStartDate();
        var endDate = newTrip.endDate() != null ? newTrip.endDate()
                : startDate.plusDays(request.getEndDate().toEpochDay() - request.getStartDate().toEpochDay());
        int days = (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
        String title = newTrip.title() != null ? newTrip.title().trim()
                : days <= 1 ? regionName + " 당일치기" : regionName + " " + (days - 1) + "박 " + days + "일";
        Companion companion = newTrip.companion() != null ? newTrip.companion() : request.getCompanion();
        ChildAgeGroup childAgeGroup = companion == Companion.KIDS ? request.getChildAgeGroup() : null;
        List<Long> regionIds = newTrip.regionIds() != null ? newTrip.regionIds()
                : !districtIds.isEmpty() && districtIds.size() <= MAX_TRIP_REGIONS ? districtIds
                : List.of(request.getSidoRegionId());
        TripOrigin origin = newTrip.origin() != null ? newTrip.origin().toOrigin()
                : request.hasOrigin() ? new TripOrigin(request.getOriginName(), request.getOriginAddress(),
                request.getOriginLat().doubleValue(), request.getOriginLng().doubleValue()) : null;
        return new NewTripCommand(title, startDate, endDate, regionIds, companion, childAgeGroup, origin);
    }

    private void publishRemoved(Long userId, CourseResponse course, List<Long> removedIds, Long tripId,
                                String companion, LocalDateTime now) {
        Set<Long> inCourse = new HashSet<>();
        course.days().forEach(d -> d.items().forEach(i -> {
            if (i instanceof CourseResponse.PlaceItem place) {
                inCourse.add(place.contentId());
            }
        }));
        for (Long id : new LinkedHashSet<>(removedIds)) {
            if (inCourse.contains(id)) {
                eventPublisher.publishEvent(new BehaviorSignalEvent(userId, id, BehaviorSignalType.AI_REMOVE, companion,
                        tripId, now));
            }
        }
    }

    private static CustomException notFound(String description) {
        return new CustomException(ErrorCode.RESOURCE_NOT_FOUND, NOT_FOUND_MESSAGE, description);
    }

    private static final class Entry {
        private final int dayNumber;
        private final int sortOrder;
        private final Long contentId;
        private final double lat;
        private final double lng;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final Integer stayMinutes;
        private final String reason;
        private final boolean required;
        private RouteResult move;

        private Entry(int dayNumber, int sortOrder, Long contentId, double lat, double lng, LocalTime startTime,
                      LocalTime endTime, Integer stayMinutes, String reason, boolean required, RouteResult move) {
            this.dayNumber = dayNumber;
            this.sortOrder = sortOrder;
            this.contentId = contentId;
            this.lat = lat;
            this.lng = lng;
            this.startTime = startTime;
            this.endTime = endTime;
            this.stayMinutes = stayMinutes;
            this.reason = reason;
            this.required = required;
            this.move = move;
        }

        private PlanItemDraft toDraft() {
            return new PlanItemDraft(contentId, dayNumber, sortOrder, startTime, endTime, stayMinutes, reason, required,
                    move);
        }
    }
}
