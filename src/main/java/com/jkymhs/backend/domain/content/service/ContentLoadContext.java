package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.category.service.CategoryMatcher;
import com.jkymhs.backend.domain.region.service.RegionCodeIndex;
import com.jkymhs.backend.domain.content.entity.IoType;

import java.util.Map;

public record ContentLoadContext(RegionCodeIndex regions, CategoryMatcher categories, Map<String, IoType> ioTypes) {
}
