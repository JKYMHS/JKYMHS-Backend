package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.client.TourApiClient;
import com.jkymhs.backend.domain.content.client.TourApiException;
import com.jkymhs.backend.domain.content.client.TourApiItems;
import com.jkymhs.backend.domain.content.dto.response.ContentDetailResponse;
import com.jkymhs.backend.domain.content.dto.response.ContentItemResponse;
import com.jkymhs.backend.domain.content.entity.Content;
import com.jkymhs.backend.domain.content.entity.ContentDetail;
import com.jkymhs.backend.domain.content.entity.ContentImage;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.repository.ContentDetailRepository;
import com.jkymhs.backend.domain.content.repository.ContentImageRepository;
import com.jkymhs.backend.domain.content.repository.ContentRepository;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// 외부 호출 중 DB 커넥션을 잡지 않으려고 클래스 트랜잭션 없음
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentDetailService {

    // 내가 건 락일 때만 지움 (남의 락 삭제 방지)
    private static final RedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final ContentService contentService;
    private final ContentRepository contentRepository;
    private final ContentDetailRepository contentDetailRepository;
    private final ContentImageRepository contentImageRepository;
    private final TourApiClient tourApiClient;
    private final StringRedisTemplate redisTemplate;
    private final ContentPersonalization personalization;
    private final ContentDetailProperties detailProperties;
    private final ExploreProperties exploreProperties;
    private final Clock clock;

    public ContentDetailResponse getDetail(Long id, Long userId) {
        Content content = findActive(id);
        LocalDateTime now = LocalDateTime.now(clock);
        ContentDetail detail = contentDetailRepository.findById(id).orElse(null);
        if (needsRefresh(detail, now) && refresh(content)) {
            content = findActive(id);
            detail = contentDetailRepository.findById(id).orElse(null);
        }
        ContentSnapshot snapshot = contentService.getActiveSnapshot(id);
        List<String> images = contentImageRepository.findAllByContentId(id).stream()
                .map(ContentImage::getImageUrl)
                .toList();
        boolean liked = personalization.likedIds(userId, List.of(id)).contains(id);
        ContentDetailResponse response = toResponse(content, snapshot, detail, images, liked, now);
        notifyViewed(userId, id);
        return response;
    }

    private Content findActive(Long id) {
        return contentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, ContentService.NOT_FOUND_MESSAGE,
                        "존재하지 않거나 삭제된 콘텐츠: " + id));
    }

    private boolean needsRefresh(ContentDetail detail, LocalDateTime now) {
        return detail == null || detail.getFetchedAt() == null || isOld(detail.getFetchedAt(), now);
    }

    private boolean isOld(LocalDateTime fetchedAt, LocalDateTime now) {
        return !fetchedAt.plus(detailProperties.refreshAfter()).isAfter(now);
    }

    private boolean refresh(Content content) {
        if (!tourApiClient.isConfigured()) {
            log.debug("TourAPI key not configured; detail refresh skipped for {}", content.getId());
            return false;
        }
        String lockKey = ContentRedisKeys.detailLock(content.getId());
        String token = UUID.randomUUID().toString();
        boolean locked = false;
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, detailProperties.lockTtl());
            if (!Boolean.TRUE.equals(acquired)) {
                return false;
            }
            locked = true;
        } catch (DataAccessException e) {
            log.warn("detail lock unavailable, refreshing without lock: {}", e.getMessage());
        }
        try {
            TourDetail fetched = fetch(content);
            if (fetched == null) {
                return false;
            }
            contentService.applyDetail(content.getId(), fetched);
            return true;
        } finally {
            if (locked) {
                releaseLock(lockKey, token);
            }
        }
    }

    private TourDetail fetch(Content content) {
        long id = content.getId();
        Map<String, String> common;
        try {
            common = tourApiClient.detailCommon(id);
        } catch (TourApiException e) {
            log.warn("TourAPI detailCommon2 failed for {}: {}", id, e.getMessage());
            return null;
        }
        if (common == null) {
            log.warn("TourAPI detailCommon2 returned no item for {}", id);
            return null;
        }
        TourIntro intro = null;
        List<String> imageUrls = null;
        Boolean petAllowed = null;
        try {
            intro = TourApiItems.toIntro(tourApiClient.detailIntro(id, content.getTourContentTypeId()));
            imageUrls = tourApiClient.detailImages(id).stream()
                    .map(item -> item.get("originimgurl"))
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(url -> !url.isEmpty())
                    .toList();
            if (content.getPetAllowed() == null) {
                petAllowed = TourApiItems.petAllowed(tourApiClient.detailPetTour(id));
            }
        } catch (TourApiException e) {
            log.warn("TourAPI detail partially failed for {}: {}", id, e.getMessage());
        }
        return new TourDetail(common.get("overview"), common.get("homepage"), intro, imageUrls, petAllowed);
    }

    private void releaseLock(String key, String token) {
        try {
            redisTemplate.execute(RELEASE_LOCK, List.of(key), token);
        } catch (DataAccessException e) {
            log.warn("detail lock release failed: {}", e.getMessage());
        }
    }

    private void notifyViewed(Long userId, Long id) {
        try {
            personalization.onViewed(userId, id);
        } catch (RuntimeException e) {
            log.warn("content view hook failed for {}: {}", id, e.getMessage());
        }
    }

    private ContentDetailResponse toResponse(Content c, ContentSnapshot s, ContentDetail detail, List<String> images,
                                             boolean liked, LocalDateTime now) {
        boolean festival = s.contentType() == ContentType.FESTIVAL;
        boolean available = detail != null && detail.getFetchedAt() != null;
        boolean stale = available && isOld(detail.getFetchedAt(), now);
        String contact = detail != null && detail.getContact() != null ? detail.getContact() : c.getTel();
        return new ContentDetailResponse(
                s.id(), s.contentType(), s.name(), s.regionName(), s.districtName(), s.address(), s.lat(), s.lng(),
                s.ioType(), s.categoryName(), s.thumbnailUrl(), images,
                detail == null ? null : detail.getOverview(),
                s.eventStartDate(), s.eventEndDate(),
                festival ? FestivalStatus.of(s.eventStartDate(), s.eventEndDate(), now.toLocalDate()) : null,
                c.getEventPlace(), s.eventStartTime(), s.eventEndTime(), c.getPlaytimeRaw(),
                c.getOperatingHoursRaw(), s.openTime(), s.closeTime(), s.lastEntryTime(),
                ContentItemResponse.restDayCodes(s), c.getRestDateRaw(),
                festival ? null : OpenStatusCalculator.of(s, now, exploreProperties.closingSoonMinutes()),
                detail == null ? null : detail.getFee(),
                detail == null ? null : detail.getHomepage(),
                contact,
                detail == null ? null : detail.getHost(),
                detail == null ? null : detail.getAgeLimit(),
                detail == null ? null : detail.getEventProgram(),
                s.petAllowed(), s.popularityScore(), s.wishCount(), liked, available, stale);
    }
}
