package com.jkymhs.backend.domain.region.controller;

import com.jkymhs.backend.domain.region.dto.response.RegionListResponse;
import com.jkymhs.backend.domain.region.service.RegionListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

    // 지역 목록은 하루에 한 번(동기화 배치) 정도만 바뀐다. 브라우저가 하루 동안 다시 묻지 않게 한다
    private static final CacheControl CACHE_ONE_DAY = CacheControl.maxAge(Duration.ofDays(1));

    private final RegionListService regionListService;

    @GetMapping
    public ResponseEntity<RegionListResponse> getRegions() {
        return ResponseEntity.ok().cacheControl(CACHE_ONE_DAY).body(regionListService.getRegionTree());
    }
}
