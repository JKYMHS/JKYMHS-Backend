package com.jkymhs.backend.domain.trip.service;

import java.time.LocalDate;

/**
 * 다른 도메인에 넘기는 여행 정보.
 *
 * @param days 여행 일수 (1박 2일 = 2)
 */
public record TripRef(Long id, LocalDate startDate, LocalDate endDate, int days) {
}
