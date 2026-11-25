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
