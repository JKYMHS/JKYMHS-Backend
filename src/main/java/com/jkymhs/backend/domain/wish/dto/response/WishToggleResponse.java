package com.jkymhs.backend.domain.wish.dto.response;

public record WishToggleResponse(Long contentId, boolean liked, int wishCount) {
}
