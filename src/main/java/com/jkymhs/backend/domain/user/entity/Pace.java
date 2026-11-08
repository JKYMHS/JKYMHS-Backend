package com.jkymhs.backend.domain.user.entity;

import com.jkymhs.backend.global.web.CodeEnum;
import lombok.RequiredArgsConstructor;

/**
 * 하루에 몇 곳을 도는지. AI 추천이 하루 장소 수를 정할 때 쓴다.
 */
@RequiredArgsConstructor
public enum Pace implements CodeEnum {

    RELAXED("relaxed"), // 하루 2~3곳
    PACKED("packed");   // 하루 4~5곳

    private final String code;

    @Override
    public String code() {
        return code;
    }
}
