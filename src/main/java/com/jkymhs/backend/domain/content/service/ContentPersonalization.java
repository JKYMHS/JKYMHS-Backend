package com.jkymhs.backend.domain.content.service;

import java.util.Collection;
import java.util.Set;

/**
 * 탐색 응답의 개인화 부분(찜 여부, 조회 기록). 탐색(공개 API)과 찜·행동 데이터(로그인 기능)를 이 인터페이스로 떼어 둔다.
 *
 * 구현은 찜 도메인의 {@code ContentPersonalizationImpl}이다 (찜 여부 = wishlists, 조회 기록 = content_views).
 * 탐색 코드는 이 인터페이스만 알아서 찜·행동 데이터 쪽이 바뀌어도 탐색 서비스를 고치지 않는다
 * (콘텐츠 도메인 → 찜 도메인 방향의 의존이 생기지 않는다).
 */
public interface ContentPersonalization {

    /**
     * @param userId null이면 비로그인 (빈 집합을 준다)
     * @return contentIds 중 그 회원이 찜한 것
     */
    Set<Long> likedIds(Long userId, Collection<Long> contentIds);

    /**
     * 상세 화면을 본 뒤에 부른다 (조회 기록·행동 데이터·인기 점수 입력). 응답을 만든 뒤에 부르므로 여기서 실패해도 상세 화면은 나가야 한다.
     *
     * @param userId null이면 비로그인
     */
    void onViewed(Long userId, Long contentId);
}
