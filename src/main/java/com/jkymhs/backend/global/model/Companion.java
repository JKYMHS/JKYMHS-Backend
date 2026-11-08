package com.jkymhs.backend.global.model;

import com.jkymhs.backend.global.web.CodeEnum;
import lombok.RequiredArgsConstructor;

/**
 * 누구와 가는지. 취향 설정·AI 추천·여행·행동 데이터가 같이 쓰므로 global에 둔다.
 * FE 값은 소문자(code), DB에는 대문자 name()이 저장된다.
 */
@RequiredArgsConstructor
public enum Companion implements CodeEnum {

    ALONE("alone"),
    COUPLE("couple"),
    FRIENDS("friends"),
    KIDS("kids"),
    PARENTS("parents");

    private final String code;

    @Override
    public String code() {
        return code;
    }

    /**
     * 아이·부모님과 가면 걷는 속도가 느리다고 보고, 경로 계산에서 도보 허용 거리를 줄이는 등에 쓴다.
     */
    public boolean slowWalker() {
        return this == KIDS || this == PARENTS;
    }
}
