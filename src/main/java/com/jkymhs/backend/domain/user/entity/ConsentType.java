package com.jkymhs.backend.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 약관 동의 항목. 필수 3개는 동의해야 가입할 수 있고, 선택 3개는 나중에 마이페이지에서 바꿀 수 있다.
 * 순서가 곧 동의 조회 응답 순서다 (FE 마이페이지 표시 순서: 필수 3개 → 위치 → 행동 데이터 → 이메일 알림).
 */
@Getter
@RequiredArgsConstructor
public enum ConsentType {
    AGE_14(true),
    TERMS(true),
    PRIVACY(true),
    // 추천 출발지를 "현재 위치"로 쓸 때 FE가 먼저 확인한다. 가입 화면에서는 받지 않아서 가입 시에는 false로 저장된다
    LOCATION(false),
    BEHAVIOR_DATA(false),
    EMAIL_NOTIFICATION(false);

    private final boolean required;
}
