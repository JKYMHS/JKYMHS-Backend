package com.jkymhs.backend.global.event;

import java.time.LocalDateTime;

/**
 * 취향 프로필에 쓸 행동 하나. 찜·여행·추천·캘린더 도메인이 발행하고 행동 데이터 도메인이 받는다.
 * 받는 쪽이 행동 데이터 동의 여부를 확인하므로, 발행하는 쪽은 동의를 신경 쓰지 않고 발행만 한다.
 *
 * @param companion  그때의 동행 FE 코드(예: "kids"). 모르면 null
 * @param tripId     여행에서 생긴 행동이면 그 여행 ID, 아니면 null
 * @param occurredAt 행동 시각(KST). 30일 반감기 계산의 기준
 */
public record BehaviorSignalEvent(
        Long userId,
        Long contentId,
        BehaviorSignalType type,
        String companion,
        Long tripId,
        LocalDateTime occurredAt
) {
}
