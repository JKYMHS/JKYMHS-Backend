package com.jkymhs.backend.domain.content.controller;

import com.jkymhs.backend.domain.content.dto.request.ContentAutocompleteRequest;
import com.jkymhs.backend.domain.content.dto.request.ContentListRequest;
import com.jkymhs.backend.domain.content.dto.request.ContentLookupRequest;
import com.jkymhs.backend.domain.content.dto.request.ContentNearbyRequest;
import com.jkymhs.backend.domain.content.dto.response.ContentAutocompleteResponse;
import com.jkymhs.backend.domain.content.dto.response.ContentDetailResponse;
import com.jkymhs.backend.domain.content.dto.response.ContentListResponse;
import com.jkymhs.backend.domain.content.dto.response.ContentLookupResponse;
import com.jkymhs.backend.domain.content.dto.response.ContentNearbyResponse;
import com.jkymhs.backend.domain.content.dto.response.NearbyPicksResponse;
import com.jkymhs.backend.domain.content.service.ContentAutocompleteService;
import com.jkymhs.backend.domain.content.service.ContentDetailService;
import com.jkymhs.backend.domain.content.service.ContentExploreService;
import com.jkymhs.backend.domain.content.service.ContentNearbyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 탐색 공개 API. 토큰이 없어도 되고, 있으면 찜 여부(liked)를 채운다.
 *
 * SecurityConfig가 GET /api/contents/**를 permitAll로 열어 둔다. 토큰이 없거나 만료·위조된 토큰이면 필터가 인증 정보를 채우지 않아서
 * {@code @AuthenticationPrincipal Long userId}가 null이다 (비로그인으로 보고 liked=false, 조회 기록 없음).
 *
 * 경로 /autocomplete, /nearby, /lookup은 /{id}보다 먼저 맞는다 (스프링은 변수 없는 경로를 우선한다).
 */
@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
public class ContentController {

    private final ContentExploreService contentExploreService;
    private final ContentAutocompleteService contentAutocompleteService;
    private final ContentDetailService contentDetailService;
    private final ContentNearbyService contentNearbyService;

    @GetMapping
    public ResponseEntity<ContentListResponse> getContents(@AuthenticationPrincipal Long userId,
                                                           @Valid @ModelAttribute ContentListRequest request) {
        return ResponseEntity.ok(contentExploreService.getList(request, userId));
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<ContentAutocompleteResponse> autocomplete(@ModelAttribute ContentAutocompleteRequest request) {
        return ResponseEntity.ok(contentAutocompleteService.autocomplete(request.q(), request.regionId(), request.tab()));
    }

    @GetMapping("/nearby")
    public ResponseEntity<ContentNearbyResponse> nearby(@Valid @ModelAttribute ContentNearbyRequest request) {
        return ResponseEntity.ok(contentNearbyService.nearby(request));
    }

    @GetMapping("/lookup")
    public ResponseEntity<ContentLookupResponse> lookup(@AuthenticationPrincipal Long userId,
                                                        @Valid @ModelAttribute ContentLookupRequest request) {
        return ResponseEntity.ok(contentExploreService.lookup(request.ids(), userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContentDetailResponse> getDetail(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(contentDetailService.getDetail(id, userId));
    }

    @GetMapping("/{id}/nearby-picks")
    public ResponseEntity<NearbyPicksResponse> nearbyPicks(@PathVariable Long id) {
        return ResponseEntity.ok(contentNearbyService.nearbyPicks(id));
    }
}
