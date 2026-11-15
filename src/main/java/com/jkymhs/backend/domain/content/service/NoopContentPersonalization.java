package com.jkymhs.backend.domain.content.service;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * 찜·조회 기록 기능이 생기기 전까지 쓰는 빈 구현. 찜·행동 데이터 작업(D3)이 이 클래스를 지우고 실제 구현을 등록한다.
 */
@Component
public class NoopContentPersonalization implements ContentPersonalization {

    @Override
    public Set<Long> likedIds(Long userId, Collection<Long> contentIds) {
        return Set.of();
    }

    @Override
    public void onViewed(Long userId, Long contentId) {
        // 조회 기록은 D3에서 남긴다
    }
}
