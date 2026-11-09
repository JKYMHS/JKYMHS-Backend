package com.jkymhs.backend.domain.content.service;

import java.util.Collection;
import java.util.Set;

/**
 * 탐색 응답의 개인화 부분(찜 여부, 조회 기록). 탐색(공개 API)과 찜·행동 데이터(로그인 기능)를 이 인터페이스로 떼어 둔다.
 *
 * 지금은 {@link NoopContentPersonalization}만 있다 (찜 기능이 아직 없어서 liked는 항상 false, 조회 기록은 남기지 않는다).
 * 찜·행동 데이터 작업(D3)이 실제 구현으로 바꾸고, 컨트롤러에 {@code @AuthenticationPrincipal Long userId}를 붙인다.
 * 탐색 코드는 이 인터페이스만 알아서 그때 고칠 곳이 컨트롤러 인자 하나뿐이다.
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
