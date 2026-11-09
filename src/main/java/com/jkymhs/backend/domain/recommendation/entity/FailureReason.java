package com.jkymhs.backend.domain.recommendation.entity;

import com.jkymhs.backend.global.web.CodeEnum;

public enum FailureReason implements CodeEnum {
    // 후보가 하루 2곳도 안 된다 (지역·기간이 너무 좁다)
    NOT_ENOUGH_CANDIDATES,
    // 추천 작업을 시작하지 못했다 (실행기가 가득 참)
    AI_UNAVAILABLE,
    // 그 밖의 서버 오류, 결과 저장 실패, 10분 넘게 끝나지 않은 요청
    INTERNAL;

    @Override
    public String code() {
        return name();
    }
}
