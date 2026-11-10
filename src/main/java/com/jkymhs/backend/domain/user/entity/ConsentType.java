package com.jkymhs.backend.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 약관 동의 항목. 필수 3개는 동의해야 가입할 수 있고, 선택 2개는 나중에 마이페이지에서 바꿀 수 있다.
 */
@Getter
@RequiredArgsConstructor
public enum ConsentType {
    AGE_14(true),
    TERMS(true),
    PRIVACY(true),
    BEHAVIOR_DATA(false),
    EMAIL_NOTIFICATION(false);

    private final boolean required;
}
