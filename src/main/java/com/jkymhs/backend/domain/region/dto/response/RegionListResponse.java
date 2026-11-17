package com.jkymhs.backend.domain.region.dto.response;

import java.util.List;

/**
 * 지역 선택 화면용 2단계 목록. 개수는 삭제되지 않은 관광지 전부와 끝나지 않은(진행 중·예정) 축제다.
 */
public record RegionListResponse(List<Sido> regions) {

    public record Sido(Long id, String name, int festivalCount, int attractionCount, List<District> children) {
    }

    public record District(Long id, String name, int festivalCount, int attractionCount) {
    }
}
