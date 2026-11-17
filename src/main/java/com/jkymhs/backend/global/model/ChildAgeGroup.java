package com.jkymhs.backend.global.model;

import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 동행한 아이의 나이대. FE도 대문자를 쓰므로 code = name()이다.
 */
public enum ChildAgeGroup implements CodeEnum {

    INFANT,     // 영유아
    ELEMENTARY, // 초등학생
    TEEN;       // 중고등학생

    @Override
    public String code() {
        return name();
    }
}
