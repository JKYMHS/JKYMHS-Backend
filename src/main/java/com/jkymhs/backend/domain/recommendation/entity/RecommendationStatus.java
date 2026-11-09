package com.jkymhs.backend.domain.recommendation.entity;

import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 추천 요청 상태. FE 값은 대문자 그대로다.
 * FAILED는 하루 횟수에서 빠진다 (실패하면 자동으로 돌려받는다).
 */
public enum RecommendationStatus implements CodeEnum {
    PENDING,
    DONE,
    // AI가 실패해서 서버가 만든 "기본 추천 코스"로 끝남. 횟수는 쓴 것으로 센다
    FALLBACK,
    FAILED;

    @Override
    public String code() {
        return name();
    }

    public boolean hasResult() {
        return this == DONE || this == FALLBACK;
    }
}
