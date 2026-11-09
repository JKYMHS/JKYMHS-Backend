package com.jkymhs.backend.domain.wish.dto.response;

/**
 * 찜 추가·삭제 결과. FE가 하트 상태와 찜 수를 이 값으로 바로 바꾼다.
 *
 * @param wishCount 이 콘텐츠의 전체 찜 수
 */
public record WishToggleResponse(Long contentId, boolean liked, int wishCount) {
}
