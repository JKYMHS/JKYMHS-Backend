package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.dto.response.ContentItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ContentItemAssembler {

    private final ContentService contentService;
    private final ContentPersonalization personalization;
    private final ExploreProperties exploreProperties;
    private final Clock clock;

    public List<ContentItemResponse> toItems(List<Long> ids, Long userId) {
        return toItems(ids, contentService.findSnapshots(ids), userId);
    }

    public List<ContentItemResponse> toItems(List<Long> ids, Map<Long, ContentSnapshot> snapshots, Long userId) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Set<Long> liked = personalization.likedIds(userId, ids);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ContentItemResponse> items = new ArrayList<>(ids.size());
        for (Long id : ids) {
            ContentSnapshot snapshot = snapshots.get(id);
            if (snapshot != null && !snapshot.deleted()) {
                items.add(ContentItemResponse.of(snapshot, liked.contains(id), now, exploreProperties.closingSoonMinutes()));
            }
        }
        return items;
    }
}
