package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.Plan;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.domain.trip.entity.Trip;
import com.jkymhs.backend.domain.trip.repository.PlanItemRepository;
import com.jkymhs.backend.domain.trip.repository.PlanRepository;
import com.jkymhs.backend.domain.trip.repository.TripRegionRepository;
import com.jkymhs.backend.domain.trip.repository.TripRepository;
import com.jkymhs.backend.domain.trip.repository.VisitReviewRepository;
import com.jkymhs.backend.global.event.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 회원 탈퇴 시 여행 데이터를 모두 지운다. 회원 도메인이 회원 행을 지우기 전에, 같은 트랜잭션에서(동기 리스너) 부른다.
 * 탈퇴가 롤백되면 여기서 지운 것도 같이 되돌아간다.
 *
 * 캘린더에 올라간 일정이 있는 여행마다 정리 이벤트를 먼저 발행한다 (캘린더 도메인이 구글 캘린더 일정 삭제를 맡는다).
 * 그다음 FK 순서대로 방문 확인 → 일정 항목 → 일정안 → 여행 지역 → 여행을 지운다 (각각 DELETE 한 번).
 */
@Component
@RequiredArgsConstructor
public class TripWithdrawListener {

    private final TripRepository tripRepository;
    private final TripRegionRepository tripRegionRepository;
    private final PlanRepository planRepository;
    private final PlanItemRepository planItemRepository;
    private final VisitReviewRepository visitReviewRepository;
    private final PlanService planService;

    @EventListener
    public void onUserWithdrawn(UserWithdrawnEvent event) {
        Long userId = event.userId();
        List<Long> tripIds = tripRepository.findAllByUserId(userId).stream().map(Trip::getId).toList();
        visitReviewRepository.deleteAllByUserId(userId);
        if (tripIds.isEmpty()) {
            return;
        }
        List<Plan> plans = planRepository.findAllByTripIdInOrderByIdAsc(tripIds);
        List<Long> planIds = plans.stream().map(Plan::getId).toList();
        if (!planIds.isEmpty()) {
            Map<Long, Long> tripOfPlan = plans.stream().collect(Collectors.toMap(Plan::getId, Plan::getTripId));
            planItemRepository.findAllByPlanIdInAndCalendarEventIdIsNotNull(planIds).stream()
                    .collect(Collectors.groupingBy(item -> tripOfPlan.get(item.getPlanId())))
                    .forEach((tripId, items) -> planService.publishOrphaned(userId, tripId, items));
            visitReviewRepository.deleteAllByPlanIdIn(planIds);
            planItemRepository.deleteAllByPlanIdIn(planIds);
            planRepository.deleteAllByTripIdIn(tripIds);
        }
        tripRegionRepository.deleteAllByTripIdIn(tripIds);
        tripRepository.deleteAllByUserId(userId);
    }
}
