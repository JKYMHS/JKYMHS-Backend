package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.Plan;
import com.jkymhs.backend.domain.trip.entity.Trip;
import com.jkymhs.backend.domain.trip.repository.PlanRepository;
import com.jkymhs.backend.domain.trip.repository.TripRepository;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TripAccess {
    static final String TRIP_NOT_FOUND = "존재하지 않는 여행입니다.";
    static final String PLAN_NOT_FOUND = "존재하지 않는 일정안입니다.";

    private final TripRepository tripRepository;
    private final PlanRepository planRepository;

    public Trip ownedTrip(Long userId, Long tripId) {
        return checkOwner(tripRepository.findById(tripId), userId, tripId);
    }

    public Trip ownedTripForUpdate(Long userId, Long tripId) {
        return checkOwner(tripRepository.findByIdForUpdate(tripId), userId, tripId);
    }

    public Plan planOf(Trip trip, Long planId) {
        return checkPlan(planRepository.findById(planId), trip, planId);
    }

    public Plan planOfForUpdate(Trip trip, Long planId) {
        return checkPlan(planRepository.findByIdForUpdate(planId), trip, planId);
    }

    private static Trip checkOwner(Optional<Trip> found, Long userId, Long tripId) {
        Trip trip = found.orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, TRIP_NOT_FOUND,
                "존재하지 않는 여행: " + tripId));
        if (!trip.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "다른 회원의 여행: " + tripId);
        }
        return trip;
    }

    private static Plan checkPlan(Optional<Plan> found, Trip trip, Long planId) {
        return found.filter(plan -> plan.getTripId().equals(trip.getId()))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, PLAN_NOT_FOUND,
                        "여행 " + trip.getId() + "에 없는 일정안: " + planId));
    }
}
