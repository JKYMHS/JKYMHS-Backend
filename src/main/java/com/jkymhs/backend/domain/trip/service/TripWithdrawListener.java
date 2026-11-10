package com.jkymhs.backend.domain.trip.service;

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

    /*
     * 엔티티를 불러오지 않고 id만 읽어서 DELETE 문으로 지운다. 여행 엔티티가 영속성 컨텍스트에 남아 있으면
     * 회원 도메인이 이어서 회원 엔티티를 remove할 때 Hibernate가 "지운 회원을 참조하는 여행"으로 보고 실패한다.
     */
    @EventListener
    public void onUserWithdrawn(UserWithdrawnEvent event) {
        Long userId = event.userId();
        visitReviewRepository.deleteAllByUserId(userId);
        List<Long> tripIds = tripRepository.findIdsByUserId(userId);
        if (tripIds.isEmpty()) {
            return;
        }
        Map<Long, Long> tripOfPlan = planRepository.findIdAndTripIdByTripIdIn(tripIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        if (!tripOfPlan.isEmpty()) {
            List<Long> planIds = List.copyOf(tripOfPlan.keySet());
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
