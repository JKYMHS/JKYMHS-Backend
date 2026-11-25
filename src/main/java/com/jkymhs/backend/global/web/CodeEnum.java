package com.jkymhs.backend.global.web;

import com.fasterxml.jackson.annotation.JsonValue;

public interface CodeEnum {

    @JsonValue
    String code();
}
