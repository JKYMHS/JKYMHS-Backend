package com.jkymhs.backend.global.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BehaviorSignalType {

    VIEW(1),
    WISH(3),
    SCHEDULE_SAVE(5),
    CALENDAR(5),
    VISIT_SATISFIED(10),
    VISIT_NEUTRAL(5),
    VISIT_UNSATISFIED(-3),
    AI_REMOVE(-3);

    private final int weight;
}
