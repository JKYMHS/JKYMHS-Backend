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

/**
 * 경로 계산 API. 외부 API(ODsay, 카카오모빌리티) 키를 FE에 두지 않으려고 서버가 대신 부른다.
 * 외부 API가 모두 실패해도 502를 주지 않고 어림값(isEstimated: true)으로 답한다.
 */
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
