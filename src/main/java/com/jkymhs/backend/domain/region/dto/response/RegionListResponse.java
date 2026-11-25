package com.jkymhs.backend.domain.region.dto.response;

import java.util.List;

public record RegionListResponse(List<Sido> regions) {

    public record Sido(Long id, String name, int festivalCount, int attractionCount, List<District> children) {
    }

    public record District(Long id, String name, int festivalCount, int attractionCount) {
    }
}
