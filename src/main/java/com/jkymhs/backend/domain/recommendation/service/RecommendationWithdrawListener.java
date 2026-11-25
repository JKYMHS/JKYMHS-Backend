package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequestRegionRepository;
import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequestRepository;
import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequiredPlaceRepository;
import com.jkymhs.backend.global.event.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

// 회원 삭제 전 같은 트랜잭션에서 지우는 동기 리스너
@Component
@RequiredArgsConstructor
public class RecommendationWithdrawListener {
    private final RecommendationRequestRepository requestRepository;
    private final RecommendationRequestRegionRepository regionRepository;
    private final RecommendationRequiredPlaceRepository requiredPlaceRepository;
    private final RecommendationResultStore resultStore;

    @EventListener
    public void onUserWithdrawn(UserWithdrawnEvent event) {
        List<Long> requestIds = requestRepository.findIdsByUserId(event.userId());
        if (requestIds.isEmpty()) {
            return;
        }
        regionRepository.deleteAllByRequestIdIn(requestIds);
        requiredPlaceRepository.deleteAllByRequestIdIn(requestIds);
        requestRepository.deleteAllByUserId(event.userId());
        resultStore.deleteAll(requestIds);
    }
}
