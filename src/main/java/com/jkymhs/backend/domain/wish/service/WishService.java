package com.jkymhs.backend.domain.wish.service;

import com.jkymhs.backend.domain.category.entity.CategoryTab;
import com.jkymhs.backend.domain.content.dto.response.ContentItemResponse;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.content.service.ExploreProperties;
import com.jkymhs.backend.domain.content.service.FestivalStatus;
import com.jkymhs.backend.domain.content.service.OpenStatus;
import com.jkymhs.backend.domain.content.service.OpenStatusCalculator;
import com.jkymhs.backend.domain.wish.dto.request.WishListRequest;
import com.jkymhs.backend.domain.wish.dto.response.WishItemResponse;
import com.jkymhs.backend.domain.wish.dto.response.WishListResponse;
import com.jkymhs.backend.domain.wish.dto.response.WishToggleResponse;
import com.jkymhs.backend.domain.wish.entity.Wish;
import com.jkymhs.backend.domain.wish.repository.ContentWishCount;
import com.jkymhs.backend.domain.wish.repository.WishRepository;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.global.pagination.CursorCodec;
import com.jkymhs.backend.global.pagination.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishService {
    static final String UNKNOWN_CONTENT_MESSAGE = "존재하지 않는 장소입니다.";
    private static final int DEFAULT_SIZE = 20;
    private static final String KEY_TAB = "t";
    private static final String KEY_WISHED_AT = "w";
    private static final String KEY_ID = "i";

    private final WishRepository wishRepository;
    private final ContentService contentService;
    private final ApplicationEventPublisher eventPublisher;
    private final CursorCodec cursorCodec;
    private final ExploreProperties exploreProperties;
    private final Clock clock;

    @Transactional
    public WishToggleResponse add(Long userId, Long contentId) {
        ContentSnapshot content = contentService.getActiveSnapshot(contentId);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        boolean inserted = wishRepository.insertIgnore(userId, contentId, now) == 1;
        if (!inserted) {
            return new WishToggleResponse(contentId, true, content.wishCount());
        }
        contentService.adjustWishCount(contentId, 1);
        eventPublisher.publishEvent(new BehaviorSignalEvent(userId, contentId, BehaviorSignalType.WISH, null, null, now));
        return new WishToggleResponse(contentId, true, content.wishCount() + 1);
    }

    @Transactional
    public WishToggleResponse remove(Long userId, Long contentId) {
        ContentSnapshot content = contentService.findSnapshots(List.of(contentId)).get(contentId);
        if (content == null) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, UNKNOWN_CONTENT_MESSAGE, "없는 콘텐츠: " + contentId);
        }
        boolean deleted = wishRepository.deleteByUserIdAndContentId(userId, contentId) == 1;
        if (!deleted) {
            return new WishToggleResponse(contentId, false, content.wishCount());
        }
        contentService.adjustWishCount(contentId, -1);
        return new WishToggleResponse(contentId, false, Math.max(content.wishCount() - 1, 0));
    }

    public WishListResponse getList(Long userId, WishListRequest request) {
        CategoryTab tab = request.tab();
        int size = request.size() == null ? DEFAULT_SIZE : request.size();
        Cursor after = request.cursor() == null || request.cursor().isBlank() ? null : readCursor(request.cursor(), tab);
        LocalDateTime now = LocalDateTime.now(clock);

        List<Wish> wishes = wishRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
        Map<Long, ContentSnapshot> snapshots = contentService.findSnapshots(wishes.stream().map(Wish::getContentId).toList());

        List<Row> matched = new ArrayList<>();
        int festivalCount = 0;
        int placeCount = 0;
        for (Wish wish : wishes) {
            ContentSnapshot content = snapshots.get(wish.getContentId());
            if (content == null) {
                continue;
            }
            boolean festival = content.contentType() == ContentType.FESTIVAL;
            if (festival) {
                festivalCount++;
            } else {
                placeCount++;
            }
            if (festival != (tab == CategoryTab.FESTIVAL) || isHidden(content, request, tab, now)) {
                continue;
            }
            if (after == null || after.isBefore(wish)) {
                matched.add(new Row(wish, content));
            }
        }

        List<Row> fetched = matched.subList(0, Math.min(matched.size(), size + 1));
        CursorPage<Row> page = CursorPage.of(fetched, size, row -> encodeCursor(tab, row.wish()));
        int soonMinutes = exploreProperties.closingSoonMinutes();
        List<WishItemResponse> items = page.items().stream()
                .map(row -> new WishItemResponse(ContentItemResponse.of(row.content(), true, now, soonMinutes),
                        row.wish().getCreatedAt(), row.content().deleted()))
                .toList();
        WishListResponse.TabCounts counts = after == null ? new WishListResponse.TabCounts(festivalCount, placeCount) : null;
        return new WishListResponse(items, page.nextCursor(), page.hasNext(), counts);
    }

    public Set<Long> findWishedContentIds(Long userId, Collection<Long> contentIds) {
        return wishRepository.findContentIds(userId, contentIds);
    }

    public long countByUser(Long userId) {
        return wishRepository.countByUserId(userId);
    }

    public Map<Long, Long> countCreatedSince(LocalDateTime since) {
        return wishRepository.countByContentSince(since).stream()
                .collect(Collectors.toMap(ContentWishCount::contentId, ContentWishCount::count));
    }

    @Transactional
    public void deleteAllOf(Long userId) {
        wishRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId)
                .forEach(wish -> contentService.adjustWishCount(wish.getContentId(), -1));
        wishRepository.deleteAllByUserId(userId);
    }

    private boolean isHidden(ContentSnapshot content, WishListRequest request, CategoryTab tab, LocalDateTime now) {
        if (tab == CategoryTab.FESTIVAL) {
            return Boolean.TRUE.equals(request.hideEnded())
                    && FestivalStatus.of(content.eventStartDate(), content.eventEndDate(), now.toLocalDate())
                    == FestivalStatus.ENDED;
        }
        if (!Boolean.TRUE.equals(request.hideClosedToday())) {
            return false;
        }
        boolean restToday = content.restDays() != null && content.restDays().contains(now.getDayOfWeek());
        return restToday
                || OpenStatusCalculator.of(content, now, exploreProperties.closingSoonMinutes()) == OpenStatus.CLOSED;
    }

    private String encodeCursor(CategoryTab tab, Wish wish) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(KEY_TAB, tab.code());
        values.put(KEY_WISHED_AT, wish.getCreatedAt().toString());
        values.put(KEY_ID, wish.getId());
        return cursorCodec.encode(values);
    }

    private Cursor readCursor(String raw, CategoryTab tab) {
        Map<String, Object> cursor = cursorCodec.decode(raw);
        CursorCodec.requireSame(cursor, KEY_TAB, tab.code());
        try {
            return new Cursor(LocalDateTime.parse(CursorCodec.requireString(cursor, KEY_WISHED_AT)),
                    CursorCodec.requireLong(cursor, KEY_ID));
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.INVALID_CURSOR, "커서의 찜한 시각 형식 오류");
        }
    }

    private record Row(Wish wish, ContentSnapshot content) {
    }

    private record Cursor(LocalDateTime wishedAt, long id) {
        boolean isBefore(Wish wish) {
            int byTime = wish.getCreatedAt().compareTo(wishedAt);
            return byTime < 0 || (byTime == 0 && wish.getId() < id);
        }
    }
}
