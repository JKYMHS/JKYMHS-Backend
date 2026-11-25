package com.jkymhs.backend.global.model;

import com.jkymhs.backend.global.web.CodeEnum;
import lombok.RequiredArgsConstructor;

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

    public boolean slowWalker() {
        return this == KIDS || this == PARENTS;
    }
}
