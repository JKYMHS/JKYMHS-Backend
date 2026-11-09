package com.jkymhs.backend.domain.trip.controller;

import com.jkymhs.backend.domain.trip.dto.request.ItemAddRequest;
import com.jkymhs.backend.domain.trip.dto.request.PlanCreateRequest;
import com.jkymhs.backend.domain.trip.dto.request.PlanRenameRequest;
import com.jkymhs.backend.domain.trip.dto.request.PlanSaveRequest;
import com.jkymhs.backend.domain.trip.dto.response.IdResponse;
import com.jkymhs.backend.domain.trip.dto.response.ItemAddResponse;
import com.jkymhs.backend.domain.trip.dto.response.PlanDetailResponse;
import com.jkymhs.backend.domain.trip.dto.response.PlanRenameResponse;
import com.jkymhs.backend.domain.trip.entity.Plan;
import com.jkymhs.backend.domain.trip.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일정안 API. 일정안이 그 여행 것이 아니면 404.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    // 본문 없이 보내도 된다 (이름 기본값 "직접 만든 일정안 {n}")
    @PostMapping
    public ResponseEntity<IdResponse> create(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                             @Valid @RequestBody(required = false) PlanCreateRequest request) {
        Long id = planService.create(userId, tripId, request == null ? null : request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(id));
    }

    @PostMapping("/{planId}/copy")
    public ResponseEntity<IdResponse> copy(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                           @PathVariable Long planId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(planService.copy(userId, tripId, planId)));
    }

    @GetMapping("/{planId}")
    public ResponseEntity<PlanDetailResponse> detail(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                                     @PathVariable Long planId) {
        return ResponseEntity.ok(planService.getDetail(userId, tripId, planId));
    }

    @PutMapping("/{planId}")
    public ResponseEntity<PlanDetailResponse> save(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                                   @PathVariable Long planId,
                                                   @Valid @RequestBody PlanSaveRequest request) {
        return ResponseEntity.ok(planService.save(userId, tripId, planId, request));
    }

    @PatchMapping("/{planId}")
    public ResponseEntity<PlanRenameResponse> rename(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                                     @PathVariable Long planId,
                                                     @Valid @RequestBody PlanRenameRequest request) {
        Plan plan = planService.rename(userId, tripId, planId, request.name());
        return ResponseEntity.ok(new PlanRenameResponse(plan.getId(), plan.getName()));
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                       @PathVariable Long planId) {
        planService.delete(userId, tripId, planId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{planId}/items")
    public ResponseEntity<ItemAddResponse> addItem(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                                   @PathVariable Long planId,
                                                   @Valid @RequestBody ItemAddRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.addItem(userId, tripId, planId, request));
    }
}
