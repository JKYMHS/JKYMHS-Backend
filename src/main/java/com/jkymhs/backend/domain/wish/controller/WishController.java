package com.jkymhs.backend.domain.wish.controller;

import com.jkymhs.backend.domain.wish.dto.request.WishListRequest;
import com.jkymhs.backend.domain.wish.dto.response.WishListResponse;
import com.jkymhs.backend.domain.wish.dto.response.WishToggleResponse;
import com.jkymhs.backend.domain.wish.service.WishService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 찜 API (로그인 필요). 추가·삭제는 멱등이라 둘 다 200과 현재 상태를 준다.
 */
@RestController
@RequestMapping("/api/wishes")
@RequiredArgsConstructor
public class WishController {

    private final WishService wishService;

    @PostMapping("/{contentId}")
    public ResponseEntity<WishToggleResponse> add(@AuthenticationPrincipal Long userId, @PathVariable Long contentId) {
        return ResponseEntity.ok(wishService.add(userId, contentId));
    }

    @DeleteMapping("/{contentId}")
    public ResponseEntity<WishToggleResponse> remove(@AuthenticationPrincipal Long userId,
                                                     @PathVariable Long contentId) {
        return ResponseEntity.ok(wishService.remove(userId, contentId));
    }

    @GetMapping
    public ResponseEntity<WishListResponse> getList(@AuthenticationPrincipal Long userId,
                                                    @Valid @ModelAttribute WishListRequest request) {
        return ResponseEntity.ok(wishService.getList(userId, request));
    }
}
