package com.jkymhs.backend.domain.trip.service;

/**
 * 방문 확인 메일 배치 한 번의 결과.
 */
public record VisitCheckMailResult(int sent, int failed) {
}
