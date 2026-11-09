package com.jkymhs.backend.domain.recommendation.controller;

import com.jkymhs.backend.domain.recommendation.dto.request.RecommendationCreateRequest;
import com.jkymhs.backend.domain.recommendation.dto.request.RecommendationRetryRequest;
import com.jkymhs.backend.domain.recommendation.dto.request.RecommendationSaveRequest;
import com.jkymhs.backend.domain.recommendation.dto.response.RecommendationAcceptedResponse;
import com.jkymhs.backend.domain.recommendation.dto.response.RecommendationQuotaResponse;
import com.jkymhs.backend.domain.recommendation.dto.response.RecommendationSaveResponse;
import com.jkymhs.backend.domain.recommendation.dto.response.RecommendationStatusResponse;
import com.jkymhs.backend.domain.recommendation.service.RecommendationSaveService;
import com.jkymhs.backend.domain.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 코스 추천. 요청·다시 추천은 작업을 실행기에 넣고 202로 바로 답한다. FE는 pollAfterMs(2초)마다 결과 API를 부른다.
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final RecommendationSaveService recommendationSaveService;

    @GetMapping("/quota")
    public ResponseEntity<RecommendationQuotaResponse> quota(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(recommendationService.quota(userId));
    }

    @PostMapping
    public ResponseEntity<RecommendationAcceptedResponse> create(@AuthenticationPrincipal Long userId,
                                                                 @Valid @RequestBody RecommendationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(recommendationService.create(userId, request));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<RecommendationStatusResponse> status(@AuthenticationPrincipal Long userId,
                                                               @PathVariable Long requestId) {
        return ResponseEntity.ok(recommendationService.getStatus(userId, requestId));
    }

    @PostMapping("/{requestId}/retry")
    public ResponseEntity<RecommendationAcceptedResponse> retry(@AuthenticationPrincipal Long userId,
                                                                @PathVariable Long requestId,
                                                                @RequestBody(required = false) RecommendationRetryRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(recommendationService.retry(userId, requestId, request));
    }

    @PostMapping("/{requestId}/save")
    public ResponseEntity<RecommendationSaveResponse> save(@AuthenticationPrincipal Long userId,
                                                           @PathVariable Long requestId,
                                                           @Valid @RequestBody RecommendationSaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recommendationSaveService.save(userId, requestId, request));
    }
}
