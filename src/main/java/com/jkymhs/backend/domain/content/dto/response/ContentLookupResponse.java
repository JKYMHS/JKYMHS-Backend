package com.jkymhs.backend.domain.content.dto.response;

import java.util.List;

/**
 * 비교 담기(FE localStorage에 담아 둔 id들) 조회 결과.
 *
 * @param items      보낸 순서대로, 지금 볼 수 있는 콘텐츠만
 * @param missingIds 삭제됐거나 없는 id (FE가 비교함에서 지운다)
 */
public record ContentLookupResponse(List<ContentItemResponse> items, List<Long> missingIds) {
}
