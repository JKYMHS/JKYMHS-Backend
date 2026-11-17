package com.jkymhs.backend.domain.recommendation.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * AI 추천 Redis 키. 노션 "Redis 키 설계"의 추천 항목과 같은 구조다.
 */
public final class RecommendationRedisKeys {

    private RecommendationRedisKeys() {
    }

    /**
     * 지역·기간별 후보 풀 (String, JSON [contentId], 최대 200개, 인기 점수 순, TTL 1시간).
     * 회원마다 다른 점수(취향·스타일)는 넣지 않아서 같은 조건의 모든 회원이 같이 쓴다. 콘텐츠 동기화 배치가 {@code candidates:*}를 지운다.
     * 형식: {@code candidates:{sidoId}:{districtIds 오름차순 쉼표 또는 all}:{startDate}:{endDate}}
     */
    public static String candidates(Long sidoId, Collection<Long> districtIds, LocalDate startDate, LocalDate endDate) {
        String districts = districtIds == null || districtIds.isEmpty() ? "all"
                : districtIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        return "candidates:" + sidoId + ":" + districts + ":" + startDate + ":" + endDate;
    }

    /** 추천 결과 (String, JSON {courses, indoorShortage}, TTL 24시간) */
    public static String result(Long requestId) {
        return "rec:result:" + requestId;
    }

    /** 진행 상황 (Hash {step, stepIndex, stepTotal, candidateCount, selectedCount}, TTL 1시간) */
    public static String progress(Long requestId) {
        return "rec:progress:" + requestId;
    }
}
