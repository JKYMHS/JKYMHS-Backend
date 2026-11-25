package com.jkymhs.backend.domain.wish.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.jkymhs.backend.domain.content.dto.response.ContentItemResponse;

import java.time.LocalDateTime;

public record WishItemResponse(
        @JsonUnwrapped ContentItemResponse content,
        LocalDateTime wishedAt,
        boolean deleted
) {
}
