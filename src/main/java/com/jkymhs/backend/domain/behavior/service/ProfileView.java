package com.jkymhs.backend.domain.behavior.service;

import java.util.Map;

/**
 * AI 추천이 쓰는 취향 프로필.
 *
 * @param lcls2Scores       분류 중분류 코드 → 점수 (클수록 선호, 음수 가능). 기록이 없으면 빈 맵
 * @param signalCount       이 프로필에 들어간 행동 수
 * @param companionSpecific true면 요청한 동행의 프로필, false면 공통 프로필
 */
public record ProfileView(Map<String, Double> lcls2Scores, int signalCount, boolean companionSpecific) {
}
