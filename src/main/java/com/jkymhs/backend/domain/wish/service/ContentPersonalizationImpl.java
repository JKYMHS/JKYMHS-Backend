package com.jkymhs.backend.domain.wish.service;

import com.jkymhs.backend.domain.behavior.service.ContentViewService;
import com.jkymhs.backend.domain.content.service.ContentPersonalization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * 탐색 응답의 개인화 부분 실제 구현 (D1의 빈 구현 NoopContentPersonalization을 대신한다).
 * 탐색 코드는 {@link ContentPersonalization} 인터페이스만 알아서, 찜·조회 기록이 생겨도 탐색 서비스는 바뀌지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ContentPersonalizationImpl implements ContentPersonalization {

    private final WishService wishService;
    private final ContentViewService contentViewService;

    /** 한 페이지의 카드 id를 한 번에 조회한다 (카드마다 물으면 N+1 쿼리가 된다) */
    @Override
    public Set<Long> likedIds(Long userId, Collection<Long> contentIds) {
        if (userId == null || contentIds == null || contentIds.isEmpty()) {
            return Set.of();
        }
        return wishService.findWishedContentIds(userId, contentIds);
    }

    /** 비로그인 조회는 세지 않는다 (§5 Q10) */
    @Override
    public void onViewed(Long userId, Long contentId) {
        if (userId == null) {
            return;
        }
        contentViewService.recordView(userId, contentId);
    }
}
