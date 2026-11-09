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

/**
 * 콘텐츠 id 목록 → 카드({@link ContentItemResponse}) 목록. 목록·비교 담기가 같이 쓴다.
 * 지역·소분류 이름은 {@link ContentService#findSnapshots}가 한 번에 채우고, 찜 여부는 {@link ContentPersonalization}이 한 번에 준다
 * (카드마다 따로 조회하면 N+1 쿼리가 된다).
 */
@Component
@RequiredArgsConstructor
public class ContentItemAssembler {

    private final ContentService contentService;
    private final ContentPersonalization personalization;
    private final ExploreProperties exploreProperties;
    private final Clock clock;

    /**
     * @param ids 보여줄 순서대로. 삭제됐거나 없는 id는 빠진다
     */
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
