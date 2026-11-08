package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.category.service.CategoryMatcher;
import com.jkymhs.backend.domain.region.service.RegionCodeIndex;
import com.jkymhs.backend.domain.content.entity.IoType;

import java.util.Map;

/**
 * 콘텐츠 수만 건을 적재할 때 한 번만 읽어두는 변환표 묶음 (법정동 코드 → 지역 id, 분류 코드 → 카테고리, 소분류 → 실내외).
 * 한 건마다 DB를 조회하면 3만 건 적재에 수십만 번 쿼리가 나간다.
 */
public record ContentLoadContext(RegionCodeIndex regions, CategoryMatcher categories, Map<String, IoType> ioTypes) {
}
