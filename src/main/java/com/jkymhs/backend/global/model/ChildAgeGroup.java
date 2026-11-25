package com.jkymhs.backend.global.model;

import com.jkymhs.backend.global.web.CodeEnum;

public enum ChildAgeGroup implements CodeEnum {

    INFANT,
    ELEMENTARY,
    TEEN;

    @Override
    public String code() {
        return name();
    }
}
