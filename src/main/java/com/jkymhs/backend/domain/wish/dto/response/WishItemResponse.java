package com.jkymhs.backend.domain.wish.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.jkymhs.backend.domain.content.dto.response.ContentItemResponse;

import java.time.LocalDateTime;

/**
 * 찜 목록 카드 = 탐색 카드(ContentItem) + 찜한 시각 + 삭제 여부.
 * {@code @JsonUnwrapped}로 카드 필드를 같은 높이에 펼친다 ({"id":…, "name":…, "wishedAt":…, "deleted":…}).
 * FE가 탐색과 찜 화면에서 같은 카드 컴포넌트를 쓰기 때문이다.
 *
 * @param deleted TourAPI에서 내려간 콘텐츠. FE가 "삭제된 장소"로 흐리게 보여주고 찜 해제만 할 수 있게 한다
 */
public record WishItemResponse(
        @JsonUnwrapped ContentItemResponse content,
        LocalDateTime wishedAt,
        boolean deleted
) {
}
