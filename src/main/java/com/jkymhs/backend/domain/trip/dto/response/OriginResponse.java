package com.jkymhs.backend.domain.trip.dto.response;

import com.jkymhs.backend.domain.trip.entity.TripOrigin;

public record OriginResponse(String name, String address, double lat, double lng) {

    public static OriginResponse from(TripOrigin origin) {
        return origin == null ? null : new OriginResponse(origin.name(), origin.address(), origin.lat(), origin.lng());
    }
}
