package com.jkymhs.backend.domain.place.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * GET /api/places/search?q= (출발지: 역·공항·주소).
 */
public record PlaceSearchRequest(
        @NotBlank(message = "검색어는 2자 이상 입력해주세요.")
        @Size(min = 2, max = 100, message = "검색어는 2자 이상 입력해주세요.") String q
) {

    public PlaceSearchRequest {
        q = q == null ? null : q.trim();
    }
}
