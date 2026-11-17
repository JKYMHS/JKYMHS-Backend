package com.jkymhs.backend.domain.region.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.RegionContentCount;
import com.jkymhs.backend.domain.region.dto.response.RegionListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * GET /api/regions 응답 조립. 지역(RegionService)과 지역별 콘텐츠 수(ContentService)를 합친다.
 *
 * RegionService에 넣지 않은 이유: ContentService가 이미 RegionService를 쓰고 있어서(콘텐츠 → 지역 이름),
 * RegionService가 다시 ContentService를 부르면 두 빈이 서로를 필요로 하는 순환 의존이 생긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionListService {

    private final RegionService regionService;
    private final ContentService contentService;
    private final Clock clock;

    public RegionListResponse getRegionTree() {
        List<RegionRef> regions = regionService.getAllRegions();
        Map<Long, int[]> counts = countByRegion(contentService.countActiveByRegion(LocalDate.now(clock)));

        Map<Long, List<RegionListResponse.District>> children = new HashMap<>();
        for (RegionRef region : regions) {
            if (!region.isSido()) {
                int[] count = counts.getOrDefault(region.id(), new int[2]);
                children.computeIfAbsent(region.parentId(), k -> new ArrayList<>())
                        .add(new RegionListResponse.District(region.id(), region.name(), count[0], count[1]));
            }
        }
        List<RegionListResponse.Sido> sidos = new ArrayList<>();
        for (RegionRef region : regions) {
            if (region.isSido()) {
                int[] count = counts.getOrDefault(region.id(), new int[2]);
                sidos.add(new RegionListResponse.Sido(region.id(), region.name(), count[0], count[1],
                        children.getOrDefault(region.id(), List.of())));
            }
        }
        return new RegionListResponse(sidos);
    }

    // 지역 id → {축제 수, 관광지 수}. 시·도는 아래 시·군·구를 모두 더한 값이고, 시·군·구가 없는 콘텐츠(세종 등)도 시·도에는 들어간다
    private static Map<Long, int[]> countByRegion(List<RegionContentCount> rows) {
        Map<Long, int[]> counts = new LinkedHashMap<>();
        for (RegionContentCount row : rows) {
            int index = row.contentType() == ContentType.FESTIVAL ? 0 : 1;
            counts.computeIfAbsent(row.sidoRegionId(), k -> new int[2])[index] += row.count();
            if (row.regionId() != null && !Objects.equals(row.regionId(), row.sidoRegionId())) {
                counts.computeIfAbsent(row.regionId(), k -> new int[2])[index] += row.count();
            }
        }
        return counts;
    }
}
