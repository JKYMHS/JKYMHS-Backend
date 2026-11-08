package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.global.web.CodeEnum;

import java.time.LocalDate;

/**
 * 축제 진행 상태. 날짜만 보고 계산하므로 DB에 저장하지 않는다 (저장하면 매일 자정에 상태를 바꾸는 배치가 필요하다).
 * FE 값은 대문자 그대로다. D-day 같은 표시 문구는 FE가 startDate·endDate로 만든다.
 */
public enum FestivalStatus implements CodeEnum {
    ONGOING,
    UPCOMING,
    ENDED;

    @Override
    public String code() {
        return name();
    }

    /**
     * 시작일·종료일 당일은 진행 중이다.
     *
     * @return 날짜가 없으면 null
     */
    public static FestivalStatus of(LocalDate start, LocalDate end, LocalDate today) {
        if (start == null || end == null) {
            return null;
        }
        if (today.isBefore(start)) {
            return UPCOMING;
        }
        if (today.isAfter(end)) {
            return ENDED;
        }
        return ONGOING;
    }
}
