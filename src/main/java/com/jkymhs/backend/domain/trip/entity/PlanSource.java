package com.jkymhs.backend.domain.trip.entity;

import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 일정안이 어떻게 만들어졌는지. FE도 대문자를 쓰므로 code = name()이다.
 * AI_RETRY는 "다시 추천"으로 받은 코스를 저장한 일정안이다 (화면에서 배지를 다르게 보여준다).
 */
public enum PlanSource implements CodeEnum {
    AI,
    AI_RETRY,
    MANUAL;

    @Override
    public String code() {
        return name();
    }
}
