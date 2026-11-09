package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.domain.trip.dto.request.TripTargetsRequest;
import com.jkymhs.backend.domain.trip.dto.request.TripUpdateRequest;
import com.jkymhs.backend.domain.trip.dto.response.OriginResponse;
import com.jkymhs.backend.domain.trip.dto.response.TripDetailResponse;
import com.jkymhs.backend.domain.trip.dto.response.TripTargetsResponse;
import com.jkymhs.backend.domain.trip.entity.Plan;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.domain.trip.entity.PlanSource;
import com.jkymhs.backend.domain.trip.entity.Trip;
import com.jkymhs.backend.domain.trip.entity.TripRegion;
import com.jkymhs.backend.domain.trip.event.ConfirmedPlanChangedEvent;
import com.jkymhs.backend.domain.trip.repository.PlanItemRepository;
import com.jkymhs.backend.domain.trip.repository.PlanRepository;
import com.jkymhs.backend.domain.trip.repository.TripRegionRepository;
import com.jkymhs.backend.domain.trip.repository.TripRepository;
import com.jkymhs.backend.domain.trip.repository.VisitReviewRepository;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 여행 만들기·상세·수정·삭제·복제·담기 대상 목록, 그리고 AI 추천이 쓰는 계약(createTripWithPlan, addPlan, getOwnedTrip).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    private static final String PAST_START = "지난 날짜는 선택할 수 없습니다.";
    private static final String END_BEFORE_START = "오는 날은 가는 날 이후로 선택해주세요.";
    private static final String MAX_DAYS = "여행 기간은 최대 %d일까지 선택할 수 있습니다.";
    private static final String CHILD_AGE_REQUIRED = "아이 나이를 선택해주세요.";
    private static final String INVALID_REGION = "잘못된 필터 값입니다.";
    private static final String REGION_COUNT = "지역은 1~5개 선택할 수 있습니다.";
    private static final int MAX_REGIONS = 5;

    private final TripAccess tripAccess;
    private final TripRepository tripRepository;
    private final TripRegionRepository tripRegionRepository;
    private final PlanRepository planRepository;
    private final PlanItemRepository planItemRepository;
    private final VisitReviewRepository visitReviewRepository;
    private final PlanService planService;
    private final RegionService regionService;
    private final ContentService contentService;
    private final TripProperties tripProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // ---------- 만들기 ----------

    /**
     * 여행과 빈 "기본 코스" 일정안을 만들고 그 일정안을 확정한다 (§4 #2: 여행에는 항상 확정안이 하나 있게 시작한다).
     */
    @Transactional
    public TripPlanIds create(Long userId, NewTripCommand command) {
        validatePeriod(command.startDate(), command.endDate(), false);
        Trip trip = saveTrip(userId, command);
        Plan plan = planRepository.save(Plan.create(trip.getId(), PlanService.DEFAULT_PLAN_NAME, PlanSource.MANUAL, null));
        trip.confirm(plan.getId());
        return new TripPlanIds(trip.getId(), plan.getId());
    }

    /**
     * (AI 추천 → 새 여행으로 저장) 여행을 만들고 초안 일정안을 확정안으로 넣는다.
     * 여행 만들기 API와 같은 검증을 하되 "가는 날 ≥ 오늘"은 보지 않는다 (추천을 받은 뒤 날짜가 지났을 수 있다).
     */
    @Transactional
    public TripPlanIds createTripWithPlan(Long userId, NewTripCommand command, PlanDraft draft) {
        validatePeriod(command.startDate(), command.endDate(), true);
        Trip trip = saveTrip(userId, command);
        Plan plan = planService.createFromDraft(userId, trip, draft);
        trip.confirm(plan.getId());
        return new TripPlanIds(trip.getId(), plan.getId());
    }

    /**
     * (AI 추천 → 기존 여행에 저장) 일정안을 하나 더 넣는다.
     *
     * @throws CustomException 403/404, 400 SCHEDULE_LIMIT_EXCEEDED(일정안 10개), 422 PLAN_EXCEEDS_TRIP_PERIOD
     */
    @Transactional
    public TripPlanIds addPlan(Long userId, Long tripId, PlanDraft draft, boolean makeConfirmed) {
        Trip trip = tripAccess.ownedTripForUpdate(userId, tripId);
        planService.ensurePlanCapacity(trip);
        Plan plan = planService.createFromDraft(userId, trip, draft);
        if (makeConfirmed) {
            planService.switchConfirmed(userId, trip, plan.getId());
        }
        return new TripPlanIds(trip.getId(), plan.getId());
    }

    public TripRef getOwnedTrip(Long userId, Long tripId) {
        Trip trip = tripAccess.ownedTrip(userId, tripId);
        return new TripRef(trip.getId(), trip.getStartDate(), trip.getEndDate(), trip.days());
    }

    private Trip saveTrip(Long userId, NewTripCommand command) {
        validateCompanion(command.companion(), command.childAgeGroup());
        List<Long> regionIds = validateRegions(command.regionIds());
        Trip trip = tripRepository.save(Trip.create(userId, command.title(), command.startDate(), command.endDate(),
                command.companion(), command.childAgeGroup(), command.origin()));
        tripRegionRepository.saveAll(regionIds.stream().map(id -> TripRegion.of(trip.getId(), id)).toList());
        return trip;
    }

    // ---------- 상세 ----------

    public TripDetailResponse getDetail(Long userId, Long tripId) {
        return detail(tripAccess.ownedTrip(userId, tripId));
    }

    private TripDetailResponse detail(Trip trip) {
        List<Long> regionIds = tripRegionRepository.findAllByTripIdOrderByIdAsc(trip.getId()).stream()
                .map(TripRegion::getRegionId).toList();
        Map<Long, RegionRef> refs = regionService.getRegions(regionIds);
        List<TripDetailResponse.Region> regions = regionIds.stream().filter(refs::containsKey)
                .map(refs::get).map(r -> new TripDetailResponse.Region(r.id(), r.name(), r.fullName())).toList();

        List<Plan> plans = planRepository.findAllByTripIdOrderByIdAsc(trip.getId());
        Map<Long, Integer> placeCounts = placeCounts(plans.stream().map(Plan::getId).toList());
        List<PlanItem> confirmedItems = trip.getConfirmedPlanId() == null ? List.of()
                : planItemRepository.findAllByPlanIdOrderByDayNumberAscSortOrderAscIdAsc(trip.getConfirmedPlanId());

        List<TripDetailResponse.PlanSummary> planSummaries = plans.stream()
                .map(p -> new TripDetailResponse.PlanSummary(p.getId(), p.getName(), p.getSource(),
                        trip.isConfirmed(p.getId()), placeCounts.getOrDefault(p.getId(), 0), p.getTotalMoveMinutes(),
                        p.getCreatedAt()))
                .toList();
        return new TripDetailResponse(trip.getId(), trip.getTitle(), trip.getStartDate(), trip.getEndDate(), regions,
                trip.getCompanion(), trip.getChildAgeGroup(), OriginResponse.from(trip.origin()),
                trip.getConfirmedPlanId(),
                new TripDetailResponse.Calendar(trip.getCalendarId(), TripSyncState.of(confirmedItems)),
                trip.getVersion(), planSummaries);
    }

    Map<Long, Integer> placeCounts(Collection<Long> planIds) {
        Map<Long, Integer> counts = new HashMap<>();
        if (planIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : planItemRepository.countByPlanIds(planIds)) {
            counts.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    // ---------- 수정 ----------

    /**
     * 보낸 필드만 바꾼다. 기간을 줄여서 일정안 장소가 기간 밖으로 나가면, removeOutOfRangeItems=true일 때만 그 장소들을 지우고
     * 아니면 422(description = 지워질 장소 수)로 먼저 확인을 받는다.
     */
    @Transactional
    public TripDetailResponse update(Long userId, Long tripId, TripUpdateRequest request) {
        Trip trip = tripAccess.ownedTripForUpdate(userId, tripId);

        LocalDate newStart = request.startDate() == null ? trip.getStartDate() : request.startDate();
        LocalDate newEnd = request.endDate() == null ? trip.getEndDate() : request.endDate();
        boolean startChanged = !newStart.equals(trip.getStartDate());
        boolean periodChanged = startChanged || !newEnd.equals(trip.getEndDate());

        Companion companion = request.companion() == null ? trip.getCompanion() : request.companion();
        ChildAgeGroup childAgeGroup = request.childAgeGroup() == null ? trip.getChildAgeGroup() : request.childAgeGroup();
        validateCompanion(companion, childAgeGroup);
        List<Long> regionIds = request.regionIds() == null ? null : validateRegions(request.regionIds());

        if (periodChanged) {
            // 가는 날을 바꾸지 않았으면 이미 지난 여행(진행 중 포함)이라도 오는 날만 고칠 수 있다
            validatePeriod(newStart, newEnd, !startChanged);
            int newDays = (int) ChronoUnit.DAYS.between(newStart, newEnd) + 1;
            removeOutOfRange(userId, trip, newDays, Boolean.TRUE.equals(request.removeOutOfRangeItems()));
            if (startChanged) {
                markConfirmedPlanDirty(userId, trip);
            }
            trip.changePeriod(newStart, newEnd);
        }
        if (request.title() != null) {
            trip.rename(request.title().trim());
        }
        trip.changeCompanion(companion, childAgeGroup);
        if (regionIds != null) {
            tripRegionRepository.deleteAllByTripIdIn(List.of(trip.getId()));
            tripRegionRepository.saveAll(regionIds.stream().map(id -> TripRegion.of(trip.getId(), id)).toList());
        }
        if (Boolean.TRUE.equals(request.clearOrigin())) {
            trip.changeOrigin(null);
        } else if (request.origin() != null) {
            trip.changeOrigin(request.origin().toOrigin());
        }
        trip.increaseVersion();
        return detail(trip);
    }

    private void removeOutOfRange(Long userId, Trip trip, int newDays, boolean confirmed) {
        List<Plan> plans = planRepository.findAllByTripIdOrderByIdAsc(trip.getId());
        List<Long> planIds = plans.stream().map(Plan::getId).toList();
        if (planIds.isEmpty()) {
            return;
        }
        List<PlanItem> outOfRange = planItemRepository.findAllByPlanIdInAndDayNumberGreaterThan(planIds, newDays);
        if (outOfRange.isEmpty()) {
            return;
        }
        if (!confirmed) {
            throw new CustomException(ErrorCode.PLAN_EXCEEDS_TRIP_PERIOD, String.valueOf(outOfRange.size()));
        }
        planService.publishOrphaned(userId, trip.getId(), outOfRange);
        List<Long> ids = outOfRange.stream().map(PlanItem::getId).toList();
        visitReviewRepository.deleteAllByPlanItemIdIn(ids);
        planItemRepository.deleteAllByIdIn(ids);

        // 뒤쪽 날짜를 통째로 지웠으므로 남은 항목의 순서·이동은 그대로다. 이동 시간 합계와 버전만 고친다
        Set<Long> affected = outOfRange.stream().map(PlanItem::getPlanId).collect(Collectors.toSet());
        Map<Long, Integer> totals = new HashMap<>();
        planItemRepository.findAllByPlanIdInOrderByPlanIdAscDayNumberAscSortOrderAscIdAsc(affected).stream()
                .filter(item -> item.getDayNumber() <= newDays)
                .forEach(item -> totals.merge(item.getPlanId(), item.travelMinutesOrZero(), Integer::sum));
        for (Plan plan : plans) {
            if (affected.contains(plan.getId())) {
                plan.changeTotalMoveMinutes(totals.getOrDefault(plan.getId(), 0));
                plan.increaseVersion();
            }
        }
    }

    // 가는 날이 바뀌면 캘린더에 올린 일정들의 날짜가 모두 달라진다
    private void markConfirmedPlanDirty(Long userId, Trip trip) {
        if (trip.getConfirmedPlanId() == null) {
            return;
        }
        List<PlanItem> items = planItemRepository.findAllByPlanIdInAndCalendarEventIdIsNotNull(
                List.of(trip.getConfirmedPlanId()));
        if (items.isEmpty()) {
            return;
        }
        items.forEach(PlanItem::markDirty);
        eventPublisher.publishEvent(new ConfirmedPlanChangedEvent(userId, trip.getId(), trip.getConfirmedPlanId()));
    }

    // ---------- 삭제·복제 ----------

    /**
     * 캘린더 일정 정리 이벤트를 먼저 발행하고(지운 뒤에는 event id를 읽을 수 없다), FK 순서대로 지운다:
     * 방문 확인 → 일정 항목 → 일정안 → 여행 지역 → 여행.
     */
    @Transactional
    public void delete(Long userId, Long tripId) {
        Trip trip = tripAccess.ownedTripForUpdate(userId, tripId);
        List<Long> planIds = planRepository.findAllByTripIdOrderByIdAsc(tripId).stream().map(Plan::getId).toList();
        if (!planIds.isEmpty()) {
            planService.publishOrphaned(userId, tripId, planItemRepository.findAllByPlanIdInAndCalendarEventIdIsNotNull(planIds));
            visitReviewRepository.deleteAllByPlanIdIn(planIds);
            planItemRepository.deleteAllByPlanIdIn(planIds);
            planRepository.deleteAllByTripIdIn(List.of(tripId));
        }
        tripRegionRepository.deleteAllByTripIdIn(List.of(tripId));
        tripRepository.delete(trip);
    }

    /**
     * 일정안·장소까지 모두 복사하고 같은 일정안을 확정한다. 캘린더·방문 확인 상태는 비운다 (§5 Q4).
     */
    @Transactional
    public Long copy(Long userId, Long tripId) {
        Trip source = tripAccess.ownedTrip(userId, tripId);
        Trip copy = tripRepository.save(source.copy(PlanService.withSuffix(source.getTitle(), PlanService.COPY_SUFFIX,
                Trip.TITLE_MAX_LENGTH)));
        tripRegionRepository.saveAll(tripRegionRepository.findAllByTripIdOrderByIdAsc(tripId).stream()
                .map(region -> TripRegion.of(copy.getId(), region.getRegionId())).toList());

        List<Plan> plans = planRepository.findAllByTripIdOrderByIdAsc(tripId);
        Map<Long, List<PlanItem>> itemsByPlan = planItemRepository
                .findAllByPlanIdInOrderByPlanIdAscDayNumberAscSortOrderAscIdAsc(plans.stream().map(Plan::getId).toList())
                .stream().collect(Collectors.groupingBy(PlanItem::getPlanId));
        for (Plan plan : plans) {
            Plan copiedPlan = planRepository.save(plan.copyTo(copy.getId(), plan.getName()));
            planItemRepository.saveAll(itemsByPlan.getOrDefault(plan.getId(), List.of()).stream()
                    .map(item -> item.copyTo(copiedPlan.getId())).toList());
            if (source.isConfirmed(plan.getId())) {
                copy.confirm(copiedPlan.getId());
            }
        }
        return copy.getId();
    }

    // ---------- 담기 대상 ----------

    public TripTargetsResponse getTargets(Long userId, TripTargetsRequest request) {
        LocalDate today = LocalDate.now(clock);
        ContentSnapshot content = request.contentId() == null ? null
                : contentService.getActiveSnapshot(request.contentId());
        boolean festival = content != null && content.contentType() == ContentType.FESTIVAL
                && content.eventStartDate() != null && content.eventEndDate() != null;
        Integer givenDays = request.startDate() != null && request.endDate() != null
                && !request.endDate().isBefore(request.startDate())
                ? (int) ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1 : null;

        List<Trip> trips = tripRepository.findAllByUserIdAndEndDateGreaterThanEqualOrderByStartDateAscIdAsc(userId, today);
        Map<Long, List<Plan>> plansByTrip = planRepository.findAllByTripIdInOrderByIdAsc(
                trips.stream().map(Trip::getId).toList()).stream().collect(Collectors.groupingBy(Plan::getTripId));

        List<TripTargetsResponse.Target> targets = new ArrayList<>();
        Long overlapDefault = null;
        Long dateDefault = null;
        for (Trip trip : trips) {
            List<Plan> plans = plansByTrip.getOrDefault(trip.getId(), List.of());
            boolean overlaps = festival && !trip.getEndDate().isBefore(content.eventStartDate())
                    && !trip.getStartDate().isAfter(content.eventEndDate());
            boolean dateMatched = trip.getStartDate().equals(request.startDate())
                    && trip.getEndDate().equals(request.endDate());
            boolean selectable = givenDays == null || trip.days() >= givenDays;
            if (overlaps && overlapDefault == null) {
                overlapDefault = trip.getId();
            }
            if (dateMatched && dateDefault == null) {
                dateDefault = trip.getId();
            }
            targets.add(new TripTargetsResponse.Target(trip.getId(), trip.getTitle(), trip.getStartDate(),
                    trip.getEndDate(), plans.size(), trip.getConfirmedPlanId(),
                    plans.stream().map(p -> new TripTargetsResponse.Plan(p.getId(), p.getName(),
                            trip.isConfirmed(p.getId()))).toList(),
                    IntStream.rangeClosed(1, trip.days())
                            .mapToObj(day -> new TripTargetsResponse.Day(day, trip.dateOf(day))).toList(),
                    overlaps, dateMatched, selectable));
        }
        return new TripTargetsResponse(targets, overlapDefault != null ? overlapDefault : dateDefault);
    }

    // ---------- 검증 ----------

    private void validatePeriod(LocalDate start, LocalDate end, boolean allowPastStart) {
        if (start == null || end == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "여행 날짜를 선택해주세요.", "날짜 없음");
        }
        if (!allowPastStart && start.isBefore(LocalDate.now(clock))) {
            throw new CustomException(ErrorCode.INVALID_INPUT, PAST_START, "지난 시작일 " + start);
        }
        if (end.isBefore(start)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, END_BEFORE_START, start + " > " + end);
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > tripProperties.maxDays()) {
            throw new CustomException(ErrorCode.SCHEDULE_LIMIT_EXCEEDED, MAX_DAYS.formatted(tripProperties.maxDays()),
                    "여행 기간 " + days + "일");
        }
    }

    private static void validateCompanion(Companion companion, ChildAgeGroup childAgeGroup) {
        if (companion == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "누구와 가는지 선택해주세요.", "동행 없음");
        }
        if (companion == Companion.KIDS && childAgeGroup == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT, CHILD_AGE_REQUIRED, "아이 나이 없음");
        }
    }

    // 중복은 하나로 합치고, 없는 지역이 하나라도 있으면 400 (지역 목록 API에 없는 값을 보낸 것)
    private List<Long> validateRegions(List<Long> regionIds) {
        List<Long> distinct = regionIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(regionIds));
        if (distinct.isEmpty() || distinct.size() > MAX_REGIONS) {
            throw new CustomException(ErrorCode.INVALID_INPUT, REGION_COUNT, "지역 수 " + distinct.size());
        }
        Map<Long, RegionRef> found = regionService.getRegions(distinct);
        if (found.size() != distinct.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT, INVALID_REGION, "존재하지 않는 지역 포함: " + distinct);
        }
        return distinct;
    }
}
