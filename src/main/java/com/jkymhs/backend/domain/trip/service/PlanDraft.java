package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.PlanSource;

import java.util.List;

public record PlanDraft(String name, PlanSource source, Long aiRequestId, List<PlanItemDraft> items) {
}
