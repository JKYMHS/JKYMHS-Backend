package com.jkymhs.backend.global.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 취향 프로필에 반영하는 행동 종류와 가중치. 가중치가 클수록 그 콘텐츠(의 분류·지역)를 더 좋아한다고 본다.
 * 음수는 "싫어함" 신호다 (불만족 방문, AI 추천 코스에서 뺀 장소).
 */
@Getter
@RequiredArgsConstructor
public enum BehaviorSignalType {

    VIEW(1),               // 상세 조회 (하루 1회)
    WISH(3),               // 찜
    SCHEDULE_SAVE(5),      // 일정에 담아 저장
    CALENDAR(5),           // 구글 캘린더에 올림
    VISIT_SATISFIED(10),   // 방문 확인: 만족
    VISIT_NEUTRAL(5),      // 방문 확인: 보통
    VISIT_UNSATISFIED(-3), // 방문 확인: 불만족
    AI_REMOVE(-3);         // AI 추천 코스에서 장소를 뺌

    private final int weight;
}
