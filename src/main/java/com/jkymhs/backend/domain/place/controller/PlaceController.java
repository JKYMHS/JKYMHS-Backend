package com.jkymhs.backend.domain.place.controller;

import com.jkymhs.backend.domain.place.dto.request.PlaceSearchRequest;
import com.jkymhs.backend.domain.place.dto.request.ReverseGeocodeRequest;
import com.jkymhs.backend.domain.place.dto.response.PlaceSearchResponse;
import com.jkymhs.backend.domain.place.dto.response.ReverseGeocodeResponse;
import com.jkymhs.backend.domain.place.service.PlaceSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 출발지 검색 (카카오 로컬 프록시). 로그인 사용자 전용 API다.
 * 이 브랜치에는 아직 Security가 없어서 인증 검사는 SecurityConfig가 들어오는 브랜치에서 걸린다
 * (/api/places/**는 permitAll에 넣지 않으므로 기본값 authenticated가 적용된다).
 */
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    @GetMapping("/search")
    public ResponseEntity<PlaceSearchResponse> search(@Valid @ModelAttribute PlaceSearchRequest request) {
        return ResponseEntity.ok(placeSearchService.search(request.q()));
    }

    @GetMapping("/reverse-geocode")
    public ResponseEntity<ReverseGeocodeResponse> reverseGeocode(@Valid @ModelAttribute ReverseGeocodeRequest request) {
        return ResponseEntity.ok(placeSearchService.reverseGeocode(request.lat(), request.lng()));
    }
}
