package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.route.service.RouteResult;
import com.jkymhs.backend.domain.route.service.RouteService;
import com.jkymhs.backend.domain.route.service.Segment;
import com.jkymhs.backend.domain.trip.dto.request.ItemAddRequest;
import com.jkymhs.backend.domain.trip.dto.request.PlanSaveRequest;
import com.jkymhs.backend.domain.trip.dto.response.ItemAddResponse;
import com.jkymhs.backend.domain.trip.dto.response.OriginResponse;
import com.jkymhs.backend.domain.trip.dto.response.PlanDetailResponse;
import com.jkymhs.backend.domain.trip.entity.PlaceSnapshot;
import com.jkymhs.backend.domain.trip.entity.Plan;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.domain.trip.entity.PlanSource;
import com.jkymhs.backend.domain.trip.entity.Trip;
import com.jkymhs.backend.domain.trip.event.CalendarEventRef;
import com.jkymhs.backend.domain.trip.event.CalendarEventsOrphanedEvent;
import com.jkymhs.backend.domain.trip.event.ConfirmedPlanChangedEvent;
import com.jkymhs.backend.domain.trip.event.ConfirmedPlanSwitchedEvent;
import com.jkymhs.backend.domain.trip.repository.PlanItemRepository;
import com.jkymhs.backend.domain.trip.repository.PlanRepository;
import com.jkymhs.backend.domain.trip.repository.VisitReviewRepository;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {
    static final String DEFAULT_PLAN_NAME = "기본 코스";
    static final String COPY_SUFFIX = " 복사본";
    private static final String MANUAL_PLAN_NAME = "직접 만든 일정안 %d";
    private static final String ITEMS_LIMIT_MESSAGE = "일정안에는 장소를 1~%d개 넣을 수 있습니다.";
    private static final String PLAN_LIMIT_MESSAGE = "일정안은 최대 %d개까지 만들 수 있습니다.";
    private static final String CONTENT_NOT_FOUND = "존재하지 않거나 삭제된 장소입니다.";
    private static final String DAY_OUT_OF_RANGE = "여행 기간 안의 날짜를 선택해주세요.";

    private static final Comparator<PlanItem> DAY_ORDER = Comparator.comparingInt(PlanItem::getDayNumber)
            .thenComparingInt(PlanItem::getSortOrder);

    private final TripAccess tripAccess;
    private final PlanRepository planRepository;
    private final PlanItemRepository planItemRepository;
    private final VisitReviewRepository visitReviewRepository;
    private final ContentService contentService;
    private final RouteService routeService;
    private final TripProperties tripProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PlanDetailResponse getDetail(Long userId, Long tripId, Long planId) {
        Trip trip = tripAccess.ownedTrip(userId, tripId);
        return detail(trip, tripAccess.planOf(trip, planId));
    }

    PlanDetailResponse detail(Trip trip, Plan plan) {
        List<PlanItem> items = planItemRepository.findAllByPlanIdOrderByDayNumberAscSortOrderAscIdAsc(plan.getId());
        Map<Long, ContentSnapshot> contents = contentService.findSnapshots(
                items.stream().map(PlanItem::getContentId).filter(Objects::nonNull).toList());
        Map<Integer, List<PlanItem>> byDay = items.stream()
                .collect(Collectors.groupingBy(PlanItem::getDayNumber));

        List<PlanDetailResponse.Day> days = new ArrayList<>();
        for (int day = 1; day <= trip.days(); day++) {
            LocalDate date = trip.dateOf(day);
            List<PlanDetailResponse.Item> views = new ArrayList<>();
            int warningCount = 0;
            for (PlanItem item : byDay.getOrDefault(day, List.of())) {
                ContentSnapshot content = item.getContentId() == null ? null : contents.get(item.getContentId());
                List<PlanWarning> warnings = PlanWarning.of(item, content, date);
                warningCount += warnings.size();
                views.add(itemView(item, content, warnings));
            }
            days.add(new PlanDetailResponse.Day(day, date, views.size(), warningCount, views));
        }
        return new PlanDetailResponse(plan.getId(), trip.getId(), plan.getName(), plan.getSource(),
                trip.isConfirmed(plan.getId()), plan.getVersion(), plan.getTotalMoveMinutes(),
                OriginResponse.from(trip.origin()), days);
    }

    private static PlanDetailResponse.Item itemView(PlanItem item, ContentSnapshot content, List<PlanWarning> warnings) {
        PlanDetailResponse.Move move = item.getTransportMode() == null ? null : new PlanDetailResponse.Move(
                item.getTransportMode(), item.getTravelMinutes(), item.getAltMode(), item.getAltTravelMinutes(),
                item.getRouteSummary(), item.getRoutePolyline(), item.isEstimated());
        boolean deleted = content == null || content.deleted();
        return new PlanDetailResponse.Item(item.getId(), item.getContentId(), item.getSnapshotName(),
                item.getSnapshotAddress(), item.getSnapshotImageUrl(), item.getSnapshotCategory(), item.lat(),
                item.lng(), item.getStartTime(), item.getEndTime(), item.getStayMinutes(), item.getMemo(),
                item.getAiReason(), item.isRequired(), deleted, move, warnings, CalendarState.of(item));
    }

    @Transactional
    public Long create(Long userId, Long tripId, String name) {
        Trip trip = tripAccess.ownedTripForUpdate(userId, tripId);
        long count = ensurePlanCapacity(trip);
        String planName = name == null ? MANUAL_PLAN_NAME.formatted(count + 1) : name.trim();
        return planRepository.save(Plan.create(trip.getId(), planName, PlanSource.MANUAL, null)).getId();
    }

    @Transactional
    public Long copy(Long userId, Long tripId, Long planId) {
        Trip trip = tripAccess.ownedTripForUpdate(userId, tripId);
        Plan source = tripAccess.planOf(trip, planId);
        ensurePlanCapacity(trip);
        Plan copy = planRepository.save(source.copyTo(trip.getId(), withSuffix(source.getName(), COPY_SUFFIX,
                Plan.NAME_MAX_LENGTH)));
        List<PlanItem> items = planItemRepository.findAllByPlanIdOrderByDayNumberAscSortOrderAscIdAsc(source.getId());
        planItemRepository.saveAll(items.stream().map(item -> item.copyTo(copy.getId())).toList());
        return copy.getId();
    }

    @Transactional
    public Plan rename(Long userId, Long tripId, Long planId, String name) {
        Trip trip = tripAccess.ownedTripForUpdate(userId, tripId);
        Plan plan = tripAccess.planOf(trip, planId);
        plan.rename(name.trim());
        return plan;
    }

    @Transactional
    public void delete(Long userId, Long tripId, Long planId) {
        Trip trip = tripAccess.ownedTripForUpdate(userId, tripId);
        Plan plan = tripAccess.planOf(trip, planId);
        if (planRepository.countByTripId(trip.getId()) <= 1) {
            throw new CustomException(ErrorCode.LAST_PLAN_CANNOT_DELETE, "남은 일정안 1개: " + planId);
        }
        List<PlanItem> items = planItemRepository.findAllByPlanIdOrderByDayNumberAscSortOrderAscIdAsc(planId);
        publishOrphaned(userId, trip.getId(), items);
        if (trip.isConfirmed(planId)) {
            trip.clearConfirmedPlan();
            trip.increaseVersion();
        }
        visitReviewRepository.deleteAllByPlanIdIn(List.of(planId));
        planItemRepository.deleteAllByPlanIdIn(List.of(planId));
        planRepository.deleteByIdInBulk(plan.getId());
    }

    @Transactional
    public Long confirm(Long userId, Long tripId, Long planId) {
        Trip trip = tripAccess.ownedTripForUpdate(userId, tripId);
        Plan plan = tripAccess.planOf(trip, planId);
        switchConfirmed(userId, trip, plan.getId());
        return trip.getConfirmedPlanId();
    }

    void switchConfirmed(Long userId, Trip trip, Long newPlanId) {
        Long oldPlanId = trip.getConfirmedPlanId();
        if (Objects.equals(oldPlanId, newPlanId)) {
            return;
        }
        List<CalendarEventRef> oldEvents = oldPlanId == null ? List.of()
                : CalendarEventRefs.of(planItemRepository.findAllByPlanIdInAndCalendarEventIdIsNotNull(List.of(oldPlanId)));
        eventPublisher.publishEvent(new ConfirmedPlanSwitchedEvent(userId, trip.getId(), oldPlanId, newPlanId, oldEvents));
        trip.confirm(newPlanId);
        trip.increaseVersion();
    }

    long ensurePlanCapacity(Trip trip) {
        long count = planRepository.countByTripId(trip.getId());
        if (count >= tripProperties.maxPlans()) {
            throw new CustomException(ErrorCode.SCHEDULE_LIMIT_EXCEEDED,
                    PLAN_LIMIT_MESSAGE.formatted(tripProperties.maxPlans()), "일정안 수 " + count);
        }
        return count;
    }

    @Transactional
    public PlanDetailResponse save(Long userId, Long tripId, Long planId, PlanSaveRequest request) {
        Trip trip = tripAccess.ownedTripForUpdate(userId, tripId);
        Plan plan = tripAccess.planOfForUpdate(trip, planId);
        if (request.version() != plan.getVersion()) {
            throw new CustomException(ErrorCode.SCHEDULE_VERSION_CONFLICT,
                    "요청 버전 " + request.version() + ", 현재 버전 " + plan.getVersion());
        }
        List<PlanSaveRequest.Item> requested = request.items() == null ? List.of() : request.items();
        if (requested.isEmpty() || requested.size() > tripProperties.maxItems()) {
            throw itemsLimit("장소 수 " + requested.size());
        }

        List<PlanItem> existing = planItemRepository.findAllByPlanIdOrderByDayNumberAscSortOrderAscIdAsc(planId);
        Map<Long, PlanItem> existingById = existing.stream().collect(Collectors.toMap(PlanItem::getId, i -> i));
        Map<Long, Segment> oldSegments = PlanMoves.segmentsById(existing, trip.origin());
        Set<Long> oldContentIds = existing.stream().map(PlanItem::getContentId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ContentSnapshot> contents = contentService.findSnapshots(
                requested.stream().map(PlanSaveRequest.Item::contentId).toList());

        Set<Long> keptIds = new HashSet<>();
        for (PlanSaveRequest.Item item : requested) {
            if (item.dayNumber() < 1 || item.dayNumber() > trip.days()) {
                throw itemsLimit("여행 기간 밖의 날짜 " + item.dayNumber());
            }
            if (item.itemId() != null && (!existingById.containsKey(item.itemId()) || !keptIds.add(item.itemId()))) {
                throw itemsLimit("이 일정안의 항목이 아니거나 중복된 itemId " + item.itemId());
            }
            ContentSnapshot content = contents.get(item.contentId());
            PlanItem current = item.itemId() == null ? null : existingById.get(item.itemId());
            boolean contentChanged = current == null || !item.contentId().equals(current.getContentId());
            if (content == null || (content.deleted() && contentChanged)) {
                throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, CONTENT_NOT_FOUND,
                        "존재하지 않거나 삭제된 콘텐츠: " + item.contentId());
            }
        }

        List<PlanItem> removed = existing.stream().filter(item -> !keptIds.contains(item.getId())).toList();
        // 캘린더 정리 알림 → 방문 확인 → 항목 순으로 지운다 (FK)
        publishOrphaned(userId, trip.getId(), removed);
        if (!removed.isEmpty()) {
            List<Long> removedIds = removed.stream().map(PlanItem::getId).toList();
            visitReviewRepository.deleteAllByPlanItemIdIn(removedIds);
            planItemRepository.deleteAll(removed);
        }

        Map<PlanSaveRequest.Item, Integer> orders = renumber(requested);
        List<PlanItem> saved = new ArrayList<>();
        for (PlanSaveRequest.Item item : requested) {
            int sortOrder = orders.get(item);
            ContentSnapshot content = contents.get(item.contentId());
            PlanItem current = item.itemId() == null ? null : existingById.get(item.itemId());
            if (current == null) {
                PlanItem created = PlanItem.create(planId, item.contentId(), item.dayNumber(), sortOrder, place(content));
                created.changeSchedule(item.startTime(), item.endTime(), item.stayMinutes(), item.memo());
                saved.add(planItemRepository.save(created));
                continue;
            }
            if (current.differsForCalendar(item.contentId(), item.dayNumber(), sortOrder, item.startTime(),
                    item.endTime(), item.memo())) {
                current.markDirty();
            }
            if (!item.contentId().equals(current.getContentId())) {
                current.applyPlace(item.contentId(), place(content));
            }
            current.moveTo(item.dayNumber(), sortOrder);
            current.changeSchedule(item.startTime(), item.endTime(), item.stayMinutes(), item.memo());
            saved.add(current);
        }

        saved.sort(DAY_ORDER);
        assignMoves(trip, saved, oldSegments, Map.of());
        plan.rename(request.name().trim());
        plan.changeTotalMoveMinutes(saved.stream().mapToInt(PlanItem::travelMinutesOrZero).sum());
        plan.increaseVersion();
        if (request.tripTitle() != null) {
            trip.rename(request.tripTitle().trim());
        }

        if (trip.isConfirmed(planId)) {
            eventPublisher.publishEvent(new ConfirmedPlanChangedEvent(userId, trip.getId(), planId));
        }
        Set<Long> added = saved.stream().map(PlanItem::getContentId)
                .filter(id -> !oldContentIds.contains(id)).collect(Collectors.toCollection(LinkedHashSet::new));
        publishScheduleSignals(userId, trip, added);
        return detail(trip, plan);
    }

    private static Map<PlanSaveRequest.Item, Integer> renumber(List<PlanSaveRequest.Item> items) {
        Map<PlanSaveRequest.Item, Integer> index = new IdentityHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            index.put(items.get(i), i);
        }
        Map<Integer, List<PlanSaveRequest.Item>> byDay = items.stream()
                .collect(Collectors.groupingBy(PlanSaveRequest.Item::dayNumber));
        Map<PlanSaveRequest.Item, Integer> orders = new IdentityHashMap<>();
        byDay.values().forEach(dayItems -> {
            List<PlanSaveRequest.Item> sorted = new ArrayList<>(dayItems);
            sorted.sort(Comparator.comparingInt(PlanSaveRequest.Item::sortOrder).thenComparing(index::get));
            for (int i = 0; i < sorted.size(); i++) {
                orders.put(sorted.get(i), i + 1);
            }
        });
        return orders;
    }

    @Transactional
    public ItemAddResponse addItem(Long userId, Long tripId, Long planId, ItemAddRequest request) {
        Trip trip = tripAccess.ownedTripForUpdate(userId, tripId);
        Plan plan = tripAccess.planOfForUpdate(trip, planId);
        if (trip.isEnded(LocalDate.now(clock))) {
            throw new CustomException(ErrorCode.SCHEDULE_ALREADY_ENDED, "종료일 " + trip.getEndDate());
        }
        int day = request.dayNumber();
        if (day > trip.days()) {
            throw new CustomException(ErrorCode.INVALID_INPUT, DAY_OUT_OF_RANGE, "여행 기간 밖의 날짜 " + day);
        }
        ContentSnapshot content = contentService.getActiveSnapshot(request.contentId());
        List<PlanItem> items = planItemRepository.findAllByPlanIdOrderByDayNumberAscSortOrderAscIdAsc(planId);
        if (items.size() >= tripProperties.maxItems()) {
            throw new CustomException(ErrorCode.SCHEDULE_LIMIT_EXCEEDED, "장소 수 " + items.size());
        }
        List<PlanItem> sameDay = items.stream().filter(item -> item.getDayNumber() == day).toList();
        boolean duplicate = sameDay.stream().anyMatch(item -> content.id().equals(item.getContentId()));
        if (duplicate && !Boolean.TRUE.equals(request.allowDuplicate())) {
            throw new CustomException(ErrorCode.SCHEDULE_ITEM_DUPLICATE, "같은 날 같은 장소: " + content.id());
        }

        int sortOrder = sameDay.stream().mapToInt(PlanItem::getSortOrder).max().orElse(0) + 1;
        PlanItem item = PlanItem.create(planId, content.id(), day, sortOrder, place(content));
        List<PlanItem> ordered = new ArrayList<>(items);
        ordered.add(item);
        ordered.sort(DAY_ORDER);
        Segment segment = PlanMoves.segments(ordered, trip.origin()).get(item);
        item.changeMove(segment == null ? null
                : routeService.routeWithoutExternal(segment.from(), segment.to(), trip.getCompanion().code()));
        planItemRepository.save(item);

        plan.changeTotalMoveMinutes(plan.getTotalMoveMinutes() + item.travelMinutesOrZero());
        plan.increaseVersion();
        if (trip.isConfirmed(planId)) {
            eventPublisher.publishEvent(new ConfirmedPlanChangedEvent(userId, trip.getId(), planId));
        }
        publishScheduleSignals(userId, trip, List.of(content.id()));
        return new ItemAddResponse(item.getId(), trip.getTitle(), plan.getName(), day, sortOrder,
                PlanWarning.isOutOfFestival(content, trip.dateOf(day)));
    }

    Plan createFromDraft(Long userId, Trip trip, PlanDraft draft) {
        List<PlanItemDraft> drafts = draft.items() == null ? List.of() : draft.items();
        if (drafts.size() > tripProperties.maxItems()) {
            throw itemsLimit("장소 수 " + drafts.size());
        }
        int maxDay = drafts.stream().mapToInt(PlanItemDraft::dayNumber).max().orElse(1);
        if (maxDay > trip.days() || drafts.stream().anyMatch(d -> d.dayNumber() < 1)) {
            throw new CustomException(ErrorCode.PLAN_EXCEEDS_TRIP_PERIOD,
                    "일정안 " + maxDay + "일, 여행 " + trip.days() + "일");
        }
        Map<Long, ContentSnapshot> contents = contentService.findSnapshots(
                drafts.stream().map(PlanItemDraft::contentId).toList());
        for (PlanItemDraft item : drafts) {
            ContentSnapshot content = contents.get(item.contentId());
            if (content == null || content.deleted()) {
                throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, CONTENT_NOT_FOUND,
                        "존재하지 않거나 삭제된 콘텐츠: " + item.contentId());
            }
        }

        String name = draft.name() == null || draft.name().isBlank() ? DEFAULT_PLAN_NAME
                : truncate(draft.name().trim(), Plan.NAME_MAX_LENGTH);
        Plan plan = planRepository.save(Plan.create(trip.getId(), name, draft.source(), draft.aiRequestId()));

        Map<Integer, List<PlanItemDraft>> byDay = drafts.stream().collect(Collectors.groupingBy(PlanItemDraft::dayNumber));
        List<PlanItem> created = new ArrayList<>();
        Map<PlanItem, RouteResult> provided = new HashMap<>();
        byDay.forEach((day, dayDrafts) -> {
            List<PlanItemDraft> sorted = dayDrafts.stream().sorted(Comparator.comparingInt(PlanItemDraft::sortOrder)).toList();
            for (int i = 0; i < sorted.size(); i++) {
                PlanItemDraft d = sorted.get(i);
                PlanItem item = PlanItem.create(plan.getId(), d.contentId(), day, i + 1, place(contents.get(d.contentId())));
                item.changeSchedule(d.startTime(), d.endTime(), d.stayMinutes(), null);
                item.changeAiInfo(d.aiReason(), d.isRequired());
                if (d.move() != null) {
                    provided.put(item, d.move());
                }
                created.add(item);
            }
        });
        created.sort(DAY_ORDER);
        assignMoves(trip, created, Map.of(), provided);
        planItemRepository.saveAll(created);
        plan.changeTotalMoveMinutes(created.stream().mapToInt(PlanItem::travelMinutesOrZero).sum());
        publishScheduleSignals(userId, trip, created.stream().map(PlanItem::getContentId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return plan;
    }

    private void assignMoves(Trip trip, List<PlanItem> ordered, Map<Long, Segment> oldSegments,
                             Map<PlanItem, RouteResult> provided) {
        Map<PlanItem, Segment> segments = PlanMoves.segments(ordered, trip.origin());
        String companion = trip.getCompanion().code();
        for (PlanItem item : ordered) {
            Segment segment = segments.get(item);
            if (segment == null) {
                item.changeMove(null);
            } else if (provided.containsKey(item)) {
                item.changeMove(provided.get(item));
            } else if (item.getId() == null || item.getTransportMode() == null
                    || !segment.equals(oldSegments.get(item.getId()))) {
                item.changeMove(routeService.routeWithoutExternal(segment.from(), segment.to(), companion));
            }
        }
    }

    void publishOrphaned(Long userId, Long tripId, Collection<PlanItem> items) {
        List<CalendarEventRef> refs = CalendarEventRefs.of(items);
        if (!refs.isEmpty()) {
            eventPublisher.publishEvent(new CalendarEventsOrphanedEvent(userId, tripId, refs));
        }
    }

    private void publishScheduleSignals(Long userId, Trip trip, Collection<Long> contentIds) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Long contentId : contentIds) {
            eventPublisher.publishEvent(new BehaviorSignalEvent(userId, contentId, BehaviorSignalType.SCHEDULE_SAVE,
                    trip.getCompanion().code(), trip.getId(), now));
        }
    }

    private static PlaceSnapshot place(ContentSnapshot content) {
        return new PlaceSnapshot(content.name(), content.address(), content.thumbnailUrl(),
                truncate(content.categoryName(), 50), content.lat(), content.lng());
    }

    private CustomException itemsLimit(String description) {
        return new CustomException(ErrorCode.SCHEDULE_LIMIT_EXCEEDED,
                ITEMS_LIMIT_MESSAGE.formatted(tripProperties.maxItems()), description);
    }

    static String withSuffix(String name, String suffix, int maxLength) {
        String full = name + suffix;
        if (full.length() <= maxLength) {
            return full;
        }
        return name.substring(0, Math.max(0, maxLength - suffix.length())).trim() + suffix;
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
