package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.repository.PlanRepository;
import com.jkymhs.backend.domain.trip.repository.TripRepository;
import com.jkymhs.backend.domain.user.service.WithdrawSummaryContributor;
import com.jkymhs.backend.domain.user.service.WithdrawSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 탈퇴 안내 화면의 "여행 n개, 일정안 m개가 지워집니다".
 */
@Component
@RequiredArgsConstructor
public class TripWithdrawSummaryContributor implements WithdrawSummaryContributor {

    private final TripRepository tripRepository;
    private final PlanRepository planRepository;

    @Override
    public void contribute(Long userId, Map<String, Long> counts) {
        counts.put(WithdrawSummaryService.TRIP_COUNT, tripRepository.countByUserId(userId));
        counts.put(WithdrawSummaryService.PLAN_COUNT, planRepository.countByUserId(userId));
    }
}
