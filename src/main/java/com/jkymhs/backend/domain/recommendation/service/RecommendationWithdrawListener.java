package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequestRegionRepository;
import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequestRepository;
import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequiredPlaceRepository;
import com.jkymhs.backend.global.event.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 회원 탈퇴 시 추천 요청과 그 결과를 지운다. 회원 행을 지우기 전에 같은 트랜잭션에서 부른다 (동기 리스너, FK 순서).
 * 엔티티를 불러오지 않고 DELETE 문으로 지운다: 영속성 컨텍스트에 남은 요청 엔티티가 지운 회원을 가리키면
 * 회원 삭제가 실패하기 때문이다 (여행 도메인과 같은 방식).
 * Redis 결과·진행 키도 지운다. 실패해도 TTL(최대 24시간) 뒤에 사라지므로 탈퇴를 막지 않는다.
 */
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
