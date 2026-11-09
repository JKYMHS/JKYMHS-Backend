package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.dto.request.PlanConfirmRequest;
import com.jkymhs.backend.domain.trip.dto.request.TripCreateRequest;
import com.jkymhs.backend.domain.trip.dto.request.TripListRequest;
import com.jkymhs.backend.domain.trip.dto.request.TripTargetsRequest;
import com.jkymhs.backend.domain.trip.dto.request.TripUpdateRequest;
import com.jkymhs.backend.domain.trip.dto.response.IdResponse;
import com.jkymhs.backend.domain.trip.dto.response.PlanConfirmResponse;
import com.jkymhs.backend.domain.trip.dto.response.TripCreateResponse;
import com.jkymhs.backend.domain.trip.dto.response.TripDetailResponse;
import com.jkymhs.backend.domain.trip.dto.response.TripListResponse;
import com.jkymhs.backend.domain.trip.dto.response.TripTargetsResponse;
import com.jkymhs.backend.domain.trip.service.PlanService;
import com.jkymhs.backend.domain.trip.service.TripListService;
import com.jkymhs.backend.domain.trip.service.TripPlanIds;
import com.jkymhs.backend.domain.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 API (모두 로그인 필요). 다른 회원의 여행은 403, 없는 여행은 404.
 */
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final TripListService tripListService;
    private final PlanService planService;

    @GetMapping
    public ResponseEntity<TripListResponse> list(@AuthenticationPrincipal Long userId,
                                                 @Valid @ModelAttribute TripListRequest request) {
        return ResponseEntity.ok(tripListService.list(userId, request));
    }

    @PostMapping
    public ResponseEntity<TripCreateResponse> create(@AuthenticationPrincipal Long userId,
                                                     @Valid @RequestBody TripCreateRequest request) {
        TripPlanIds ids = tripService.create(userId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(new TripCreateResponse(ids.tripId(), ids.planId()));
    }

    @GetMapping("/targets")
    public ResponseEntity<TripTargetsResponse> targets(@AuthenticationPrincipal Long userId,
                                                       @Valid @ModelAttribute TripTargetsRequest request) {
        return ResponseEntity.ok(tripService.getTargets(userId, request));
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<TripDetailResponse> detail(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.getDetail(userId, tripId));
    }

    @PatchMapping("/{tripId}")
    public ResponseEntity<TripDetailResponse> update(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                                     @Valid @RequestBody TripUpdateRequest request) {
        return ResponseEntity.ok(tripService.update(userId, tripId, request));
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        tripService.delete(userId, tripId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tripId}/copy")
    public ResponseEntity<IdResponse> copy(@AuthenticationPrincipal Long userId, @PathVariable Long tripId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(tripService.copy(userId, tripId)));
    }

    @PutMapping("/{tripId}/confirmed-plan")
    public ResponseEntity<PlanConfirmResponse> confirm(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                                       @Valid @RequestBody PlanConfirmRequest request) {
        return ResponseEntity.ok(new PlanConfirmResponse(planService.confirm(userId, tripId, request.planId())));
    }
}
