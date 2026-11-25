package com.jkymhs.backend.domain.region.service;

import com.jkymhs.backend.domain.region.entity.Region;

public record RegionRef(Long id, String name, int depth, Long parentId, String parentName, String fullName) {

    static RegionRef from(Region region) {
        Region parent = region.getParent();
        if (parent == null) {
            return new RegionRef(region.getId(), region.getShortName(), region.getDepth(), null, null, region.getShortName());
        }
        return new RegionRef(region.getId(), region.getShortName(), region.getDepth(), parent.getId(),
                parent.getShortName(), parent.getShortName() + " " + region.getShortName());
    }

    public boolean isSido() {
        return depth == Region.DEPTH_SIDO;
    }
}
