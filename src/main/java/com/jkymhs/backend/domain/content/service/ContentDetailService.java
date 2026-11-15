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

/**
 * 콘텐츠 상세 (GET /api/contents/{id}).
 *
 * 소개글·사진은 CSV 초기 적재에 없어서 처음 볼 때 TourAPI에서 받아 DB에 저장하고, 7일(content-detail.refresh-after) 동안은 저장된 값을 쓴다.
 * 매 조회마다 TourAPI를 부르면 하루 호출 한도(개발 키 1000회)를 금방 다 쓰고 응답도 느려진다.
 *
 * <h3>갱신 순서</h3>
 * <ol>
 *   <li>content_details 행이 없거나, 상세를 받은 적이 없거나(fetched_at NULL: CSV·운영 정보로만 만든 행), 받은 지 7일이 지났으면 갱신한다.</li>
 *   <li>Redis {@code SET lock:detail:{id} <uuid> NX PX 10000}으로 락을 잡는다. 인기 콘텐츠를 여러 명이 동시에 열어도 TourAPI는 한 번만 부른다.
 *       락을 못 잡은 요청(다른 요청이 갱신 중)은 기다리지 않고 저장된 값을 보여준다. Redis가 안 되면 락 없이 갱신한다(캐시 장애로 상세가 막히지 않게).</li>
 *   <li>detailCommon2(소개글) → detailIntro2(운영 정보) → detailImage2(사진) → 반려동물 정보를 모르면 detailPetTour2.
 *       소개글 호출이 실패하면 아무것도 저장하지 않는다. 그 뒤 호출이 실패하면 받은 데까지만 저장한다 (나머지는 기존 값 유지).</li>
 *   <li>락은 Lua 스크립트로 "내가 건 값일 때만" 지운다. 갱신이 10초를 넘겨 락이 풀린 뒤 다른 요청이 새로 건 락을 지우지 않기 위해서다.</li>
 * </ol>
 *
 * TourAPI가 실패해도 200이다: 저장된 상세가 있으면 그것을 주고 {@code isStale=true}, 없으면 기본 정보만 주고 {@code detailAvailable=false}
 * (FE가 상세 영역에 재시도 안내를 띄운다, PRD 부분 실패 처리).
 *
 * 클래스에 트랜잭션을 걸지 않는다. 외부 호출(최대 수 초)을 하는 동안 DB 커넥션을 잡고 있지 않게 하고,
 * 갱신 뒤 다시 읽을 때 새 값이 보이게 하기 위해서다 (저장은 {@link ContentService#applyDetail}이 자기 트랜잭션으로 한다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentDetailService {

    // GET으로 값을 확인하고 같을 때만 DEL한다. 두 명령 사이에 다른 요청이 끼지 않게 Redis 안에서 한 번에 실행한다
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

    /**
     * @param userId 비로그인이면 null
     * @throws CustomException 404 RESOURCE_NOT_FOUND "존재하지 않거나 삭제된 장소입니다."
     */
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

    /**
     * @return 새 상세를 저장했으면 true
     */
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

    // 소개글(detailCommon2)은 꼭 있어야 하고, 나머지는 받은 데까지만 쓴다. null로 둔 부분은 applyDetail이 기존 값을 그대로 둔다
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
            // 못 지워도 10초 뒤에 저절로 풀린다
            log.warn("detail lock release failed: {}", e.getMessage());
        }
    }

    // 조회 기록은 부가 기능이라 실패해도 상세 응답은 나가야 한다
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
