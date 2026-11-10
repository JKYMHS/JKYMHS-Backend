package com.jkymhs.backend.domain.trip.entity;

/**
 * 일정 항목에 복사해 두는 장소 정보 (plan_items.snapshot_* 컬럼 묶음).
 *
 * @param category 소분류 이름 ("박물관")
 */
public record PlaceSnapshot(String name, String address, String imageUrl, String category, double lat, double lng) {
}
