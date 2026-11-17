package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.route.service.LatLng;
import com.jkymhs.backend.domain.route.service.Segment;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.domain.trip.entity.TripOrigin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 일정 항목마다 "어디서 오는지"(구간)를 정한다. 그날 앞 항목에서 오고, 첫째 날 첫 항목은 출발지에서 온다.
 * 둘째 날부터의 첫 항목은 전날 숙소를 모르므로 이동이 없다.
 */
final class PlanMoves {

    private PlanMoves() {
    }

    /**
     * @param ordered 날짜 → 순서로 정렬된 항목들
     * @return 항목 → 들어오는 구간. 이동이 없는 항목은 값이 null
     */
    static Map<PlanItem, Segment> segments(List<PlanItem> ordered, TripOrigin origin) {
        Map<PlanItem, Segment> segments = new HashMap<>();
        PlanItem previous = null;
        for (PlanItem item : ordered) {
            LatLng to = new LatLng(item.lat(), item.lng());
            LatLng from = null;
            if (previous != null && previous.getDayNumber() == item.getDayNumber()) {
                from = new LatLng(previous.lat(), previous.lng());
            } else if (item.getDayNumber() == 1 && origin != null) {
                from = new LatLng(origin.lat(), origin.lng());
            }
            segments.put(item, from == null ? null : new Segment(from, to));
            previous = item;
        }
        return segments;
    }

    /** 항목 id → 구간 (저장 전 상태를 기억해 두고 구간이 바뀐 항목만 경로를 다시 정하려고) */
    static Map<Long, Segment> segmentsById(List<PlanItem> ordered, TripOrigin origin) {
        Map<Long, Segment> byId = new HashMap<>();
        segments(ordered, origin).forEach((item, segment) -> byId.put(item.getId(), segment));
        return byId;
    }
}
