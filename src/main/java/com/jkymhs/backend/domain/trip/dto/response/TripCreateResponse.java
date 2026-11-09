package com.jkymhs.backend.domain.trip.dto.response;

/**
 * @param planId 함께 만든 "기본 코스" 일정안 (확정 상태)
 */
public record TripCreateResponse(Long id, Long planId) {
}
