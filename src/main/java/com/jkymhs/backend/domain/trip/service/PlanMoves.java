package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.route.service.LatLng;
import com.jkymhs.backend.domain.route.service.Segment;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.domain.trip.entity.TripOrigin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PlanMoves {
    private PlanMoves() {
    }

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

    static Map<Long, Segment> segmentsById(List<PlanItem> ordered, TripOrigin origin) {
        Map<Long, Segment> byId = new HashMap<>();
        segments(ordered, origin).forEach((item, segment) -> byId.put(item.getId(), segment));
        return byId;
    }
}
