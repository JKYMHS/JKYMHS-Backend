package com.jkymhs.backend.domain.trip.dto.request;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * "여행에 담기" 대상 목록 쿼리 파라미터. 상세 화면의 장소(contentId)와 탐색에서 고른 날짜(startDate·endDate)를 보낸다.
 */
public record TripTargetsRequest(
        Long contentId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
) {
}
