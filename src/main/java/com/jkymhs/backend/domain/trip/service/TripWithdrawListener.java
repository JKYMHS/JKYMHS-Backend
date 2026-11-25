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
