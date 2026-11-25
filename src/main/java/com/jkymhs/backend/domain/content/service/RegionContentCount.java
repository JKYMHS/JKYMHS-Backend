package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.entity.ContentType;

public record RegionContentCount(Long sidoRegionId, Long regionId, ContentType contentType, int count) {
}
