package com.jkymhs.backend.domain.content.service;

import java.util.Collection;
import java.util.Set;

public interface ContentPersonalization {

    Set<Long> likedIds(Long userId, Collection<Long> contentIds);

    void onViewed(Long userId, Long contentId);
}
