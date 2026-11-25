package com.jkymhs.backend.domain.user.controller;

import com.jkymhs.backend.domain.user.dto.response.TravelStyleListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travel-styles")
public class TravelStyleController {

    @GetMapping
    public ResponseEntity<TravelStyleListResponse> getTravelStyles() {
        return ResponseEntity.ok(TravelStyleListResponse.all());
    }
}
