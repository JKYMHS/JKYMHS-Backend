package com.jkymhs.backend.domain.wish.service;

import com.jkymhs.backend.domain.behavior.service.ContentViewService;
import com.jkymhs.backend.domain.content.service.ContentPersonalization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ContentPersonalizationImpl implements ContentPersonalization {
    private final WishService wishService;
    private final ContentViewService contentViewService;

    @Override
    public Set<Long> likedIds(Long userId, Collection<Long> contentIds) {
        if (userId == null || contentIds == null || contentIds.isEmpty()) {
            return Set.of();
        }
        return wishService.findWishedContentIds(userId, contentIds);
    }

    @Override
    public void onViewed(Long userId, Long contentId) {
        if (userId == null) {
            return;
        }
        contentViewService.recordView(userId, contentId);
    }
}
