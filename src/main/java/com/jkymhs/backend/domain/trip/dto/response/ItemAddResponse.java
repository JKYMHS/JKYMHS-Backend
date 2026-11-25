package com.jkymhs.backend.domain.trip.dto.response;

public record ItemAddResponse(
        Long itemId,
        String tripTitle,
        String planName,
        int dayNumber,
        int sortOrder,
        boolean festivalOutOfRange
) {
}
