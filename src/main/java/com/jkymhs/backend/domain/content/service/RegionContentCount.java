package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.entity.ContentType;

/**
 * 지역·종류별 콘텐츠 수 (지역 목록 API의 축제·관광지 개수).
 *
 * @param regionId 시·군·구 id. 시·군·구가 없는 콘텐츠면 시·도 id와 같다
 */
public record RegionContentCount(Long sidoRegionId, Long regionId, ContentType contentType, int count) {
}
