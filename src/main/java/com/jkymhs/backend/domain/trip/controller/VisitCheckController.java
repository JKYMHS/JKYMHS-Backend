package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.dto.request.VisitCheckSaveRequest;
import com.jkymhs.backend.domain.trip.dto.response.VisitCheckDismissResponse;
import com.jkymhs.backend.domain.trip.dto.response.VisitCheckPendingResponse;
import com.jkymhs.backend.domain.trip.dto.response.VisitCheckResponse;
import com.jkymhs.backend.domain.trip.dto.response.VisitCheckSaveResponse;
import com.jkymhs.backend.domain.trip.service.VisitCheckService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 끝난 여행의 방문 확인 API.
 */
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class VisitCheckController {

    private final VisitCheckService visitCheckService;

    @GetMapping("/visit-check/pending")
    public ResponseEntity<VisitCheckPendingResponse> pending(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(visitCheckService.getPending(userId));
    }

    @GetMapping("/{tripId}/visit-check")
    public ResponseEntity<VisitCheckResponse> get(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        return ResponseEntity.ok(visitCheckService.get(userId, tripId));
    }

    @PutMapping("/{tripId}/visit-check")
    public ResponseEntity<VisitCheckSaveResponse> save(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                                       @Valid @RequestBody VisitCheckSaveRequest request) {
        return ResponseEntity.ok(visitCheckService.save(userId, tripId, request));
    }

    @PostMapping("/{tripId}/visit-check/dismiss")
    public ResponseEntity<VisitCheckDismissResponse> dismiss(@AuthenticationPrincipal Long userId,
                                                             @PathVariable Long tripId) {
        return ResponseEntity.ok(visitCheckService.dismiss(userId, tripId));
    }
}
