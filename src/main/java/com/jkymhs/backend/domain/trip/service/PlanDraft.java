package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.PlanSource;

import java.util.List;

/**
 * 다른 도메인(AI 추천)이 일정안을 통째로 만들 때 넘기는 값.
 *
 * @param aiRequestId AI 추천 요청 id. 직접 만든 안이면 null
 */
public record PlanDraft(String name, PlanSource source, Long aiRequestId, List<PlanItemDraft> items) {
}
