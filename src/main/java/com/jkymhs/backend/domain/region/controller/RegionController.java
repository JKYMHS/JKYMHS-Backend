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

    private static final CacheControl CACHE_ONE_DAY = CacheControl.maxAge(Duration.ofDays(1));

    private final RegionListService regionListService;

    @GetMapping
    public ResponseEntity<RegionListResponse> getRegions() {
        return ResponseEntity.ok().cacheControl(CACHE_ONE_DAY).body(regionListService.getRegionTree());
    }
}
