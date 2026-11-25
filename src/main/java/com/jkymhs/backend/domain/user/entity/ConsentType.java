package com.jkymhs.backend.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConsentType {
    AGE_14(true),
    TERMS(true),
    PRIVACY(true),
    LOCATION(false),
    BEHAVIOR_DATA(false),
    EMAIL_NOTIFICATION(false);

    private final boolean required;
}
