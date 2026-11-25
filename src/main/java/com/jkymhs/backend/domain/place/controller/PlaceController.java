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
