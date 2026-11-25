package com.jkymhs.backend.domain.route.controller;

import com.jkymhs.backend.domain.route.dto.request.RouteCalculateRequest;
import com.jkymhs.backend.domain.route.dto.response.RouteCalculateResponse;
import com.jkymhs.backend.domain.route.dto.response.RouteResponse;
import com.jkymhs.backend.domain.route.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {
    private final RouteService routeService;

    @PostMapping
    public ResponseEntity<RouteCalculateResponse> calculate(@Valid @RequestBody RouteCalculateRequest request) {
        String companion = request.companion() == null ? null : request.companion().code();
        return ResponseEntity.ok(new RouteCalculateResponse(routeService.routes(request.toSegments(), companion).stream()
                .map(RouteResponse::from).toList()));
    }
}
