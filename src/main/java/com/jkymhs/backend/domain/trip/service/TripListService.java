package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.domain.trip.dto.request.TripListRequest;
import com.jkymhs.backend.domain.trip.dto.response.TripListResponse;
import com.jkymhs.backend.domain.trip.dto.response.TripSummaryResponse;
import com.jkymhs.backend.domain.trip.entity.Plan;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.domain.trip.entity.Trip;
import com.jkymhs.backend.domain.trip.entity.TripRegion;
import com.jkymhs.backend.domain.trip.entity.VisitReview;
import com.jkymhs.backend.domain.trip.repository.PlanItemRepository;
import com.jkymhs.backend.domain.trip.repository.PlanRepository;
import com.jkymhs.backend.domain.trip.repository.TripRegionRepository;
import com.jkymhs.backend.domain.trip.repository.TripRepository;
import com.jkymhs.backend.domain.trip.repository.VisitReviewRepository;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.global.pagination.CursorCodec;
import com.jkymhs.backend.global.pagination.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 여행 목록 (내 여행 화면).
 *
 * 정렬: 다가오는 여행(종료일 ≥ 오늘)을 시작일 순으로 먼저, 그다음 지난 여행을 시작일 역순으로. 두 묶음은 정렬 방향이 달라서
 * 쿼리 하나로 커서를 만들 수 없다. 그래서 다가오는 쪽을 먼저 읽고, 한 페이지가 덜 차면 지난 쪽에서 이어 채운다.
 * 커서 {f: 필터, g: 마지막 항목의 묶음, d: 시작일, i: id}로 어느 묶음의 어디까지 읽었는지 기억한다.
 *
 * 카드에 필요한 지역·일정안·장소·방문 확인은 페이지의 여행 id로 한 번씩만 모아 읽는다 (여행마다 쿼리를 날리는 N+1을 피한다).
 *
 * 첫 페이지에서 방문 확인 배너 여행을 찾으면서 그 여행 상태를 PENDING으로 바꿀 수 있어서 쓰기 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripListService {

    private final TripRepository tripRepository;
    private final TripRegionRepository tripRegionRepository;
    private final PlanRepository planRepository;
    private final PlanItemRepository planItemRepository;
    private final VisitReviewRepository visitReviewRepository;
    private final RegionService regionService;
    private final VisitCheckService visitCheckService;
    private final CursorCodec cursorCodec;
    private final Clock clock;

    @Transactional
    public TripListResponse list(Long userId, TripListRequest request) {
        TripFilter filter = request.filterOrDefault();
        int size = request.sizeOrDefault();
        LocalDate today = LocalDate.now(clock);
        Cursor cursor = request.cursor() == null ? null : decode(request.cursor(), filter);

        boolean unconfirmedOnly = filter == TripFilter.UNCONFIRMED;
        int need = size + 1;
        List<Trip> rows = new ArrayList<>();
        if (filter != TripFilter.PAST && (cursor == null || cursor.group() == TripGroup.UPCOMING)) {
            rows.addAll(tripRepository.findUpcomingPage(userId, today, unconfirmedOnly,
                    cursor == null ? null : cursor.date(), cursor == null ? null : cursor.id(), PageRequest.of(0, need)));
        }
        if (filter != TripFilter.UPCOMING && rows.size() < need) {
            boolean afterPast = cursor != null && cursor.group() == TripGroup.PAST;
            rows.addAll(tripRepository.findPastPage(userId, today, unconfirmedOnly,
                    afterPast ? cursor.date() : null, afterPast ? cursor.id() : null,
                    PageRequest.of(0, need - rows.size())));
        }

        CursorPage<Trip> page = CursorPage.of(rows, size, trip -> cursorCodec.encode(Map.of(
                "f", filter.code(),
                "g", TripGroup.of(trip.getEndDate(), today).code(),
                "d", trip.getStartDate().toString(),
                "i", trip.getId())));
        List<TripSummaryResponse> items = summaries(page.items(), today);

        TripListResponse.Counts counts = null;
        TripListResponse.VisitCheckPending pending = null;
        if (cursor == null) {
            counts = new TripListResponse.Counts(tripRepository.countByUserId(userId),
                    tripRepository.countByUserIdAndEndDateGreaterThanEqual(userId, today),
                    tripRepository.countByUserIdAndEndDateBefore(userId, today),
                    tripRepository.countByUserIdAndConfirmedPlanIdIsNull(userId));
            pending = visitCheckService.findPendingTrip(userId)
                    .map(trip -> new TripListResponse.VisitCheckPending(trip.getId(), trip.getTitle(), trip.getEndDate()))
                    .orElse(null);
        }
        return new TripListResponse(items, page.nextCursor(), page.hasNext(), counts, pending);
    }

    private List<TripSummaryResponse> summaries(List<Trip> trips, LocalDate today) {
        if (trips.isEmpty()) {
            return List.of();
        }
        List<Long> tripIds = trips.stream().map(Trip::getId).toList();

        Map<Long, List<Long>> regionIdsByTrip = tripRegionRepository.findAllByTripIdInOrderByIdAsc(tripIds).stream()
                .collect(Collectors.groupingBy(TripRegion::getTripId, LinkedHashMap::new,
                        Collectors.mapping(TripRegion::getRegionId, Collectors.toList())));
        Map<Long, RegionRef> regionRefs = regionService.getRegions(regionIdsByTrip.values().stream()
                .flatMap(Collection::stream).collect(Collectors.toSet()));

        Map<Long, List<Plan>> plansByTrip = planRepository.findAllByTripIdInOrderByIdAsc(tripIds).stream()
                .collect(Collectors.groupingBy(Plan::getTripId));
        // 카드에 보여줄 일정안: 확정안, 없으면 첫 일정안
        Map<Long, Plan> displayPlans = new LinkedHashMap<>();
        for (Trip trip : trips) {
            List<Plan> plans = plansByTrip.getOrDefault(trip.getId(), List.of());
            plans.stream().filter(p -> trip.isConfirmed(p.getId())).findFirst()
                    .or(() -> plans.stream().findFirst())
                    .ifPresent(plan -> displayPlans.put(trip.getId(), plan));
        }
        Map<Long, List<PlanItem>> itemsByPlan = planItemRepository
                .findAllByPlanIdInOrderByPlanIdAscDayNumberAscSortOrderAscIdAsc(
                        displayPlans.values().stream().map(Plan::getId).toList())
                .stream().collect(Collectors.groupingBy(PlanItem::getPlanId));

        // 끝난 여행의 확정안 장소 중 방문 확인에 답한 것
        List<Long> pastConfirmedItemIds = trips.stream()
                .filter(trip -> trip.isEnded(today) && trip.getConfirmedPlanId() != null)
                .flatMap(trip -> itemsByPlan.getOrDefault(trip.getConfirmedPlanId(), List.of()).stream())
                .map(PlanItem::getId).toList();
        Set<Long> reviewedItemIds = pastConfirmedItemIds.isEmpty() ? Set.of()
                : visitReviewRepository.findAllByPlanItemIdIn(pastConfirmedItemIds).stream()
                .map(VisitReview::getPlanItemId).collect(Collectors.toSet());

        List<TripSummaryResponse> summaries = new ArrayList<>();
        for (Trip trip : trips) {
            Plan display = displayPlans.get(trip.getId());
            List<PlanItem> displayItems = display == null ? List.of() : itemsByPlan.getOrDefault(display.getId(), List.of());
            boolean confirmed = display != null && trip.isConfirmed(display.getId());
            List<PlanItem> confirmedItems = confirmed ? displayItems : List.of();

            TripSummaryResponse.ConfirmedPlan confirmedPlan = confirmed
                    ? new TripSummaryResponse.ConfirmedPlan(display.getId(), display.getName(), confirmedItems.size())
                    : null;
            TripSummaryResponse.Visit visit = confirmed && trip.isEnded(today)
                    ? new TripSummaryResponse.Visit((int) confirmedItems.stream()
                    .filter(item -> reviewedItemIds.contains(item.getId())).count(), confirmedItems.size())
                    : null;
            String thumbnail = displayItems.stream().map(PlanItem::getSnapshotImageUrl).filter(Objects::nonNull)
                    .findFirst().orElse(null);
            List<TripSummaryResponse.Region> regions = regionIdsByTrip.getOrDefault(trip.getId(), List.of()).stream()
                    .filter(regionRefs::containsKey).map(regionRefs::get)
                    .map(ref -> new TripSummaryResponse.Region(ref.id(), ref.name())).toList();

            summaries.add(new TripSummaryResponse(trip.getId(), trip.getTitle(), trip.getStartDate(), trip.getEndDate(),
                    TripGroup.of(trip.getEndDate(), today), regions, trip.getCompanion(), trip.getOriginName(),
                    plansByTrip.getOrDefault(trip.getId(), List.of()).size(), confirmedPlan, visit,
                    TripSyncState.of(confirmedItems), thumbnail));
        }
        return summaries;
    }

    private Cursor decode(String raw, TripFilter filter) {
        Map<String, Object> values = cursorCodec.decode(raw);
        CursorCodec.requireSame(values, "f", filter.code());
        String group = CursorCodec.requireString(values, "g");
        TripGroup tripGroup = switch (group) {
            case "upcoming" -> TripGroup.UPCOMING;
            case "past" -> TripGroup.PAST;
            default -> throw invalidCursor("알 수 없는 묶음 " + group);
        };
        if ((filter == TripFilter.UPCOMING && tripGroup == TripGroup.PAST)
                || (filter == TripFilter.PAST && tripGroup == TripGroup.UPCOMING)) {
            throw invalidCursor("필터와 맞지 않는 묶음");
        }
        try {
            LocalDate date = LocalDate.parse(CursorCodec.requireString(values, "d"));
            return new Cursor(tripGroup, date, CursorCodec.requireLong(values, "i"));
        } catch (DateTimeParseException e) {
            throw invalidCursor("날짜 형식 오류");
        }
    }

    private static CustomException invalidCursor(String description) {
        return new CustomException(ErrorCode.INVALID_CURSOR, description);
    }

    private record Cursor(TripGroup group, LocalDate date, long id) {
    }
}
