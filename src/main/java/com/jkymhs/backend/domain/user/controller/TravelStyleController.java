package com.jkymhs.backend.domain.user.controller;

import com.jkymhs.backend.domain.user.dto.response.TravelStyleListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 스타일 목록 (공개). 온보딩 화면이 로그인 직후에 그리지만, 값이 고정이라 로그인 없이도 열어둔다.
 */
@RestController
@RequestMapping("/api/travel-styles")
public class TravelStyleController {

    @GetMapping
    public ResponseEntity<TravelStyleListResponse> getTravelStyles() {
        return ResponseEntity.ok(TravelStyleListResponse.all());
    }
}
