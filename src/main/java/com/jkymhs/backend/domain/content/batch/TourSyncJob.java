package com.jkymhs.backend.domain.content.batch;

import com.jkymhs.backend.domain.content.client.TourApiClient;
import com.jkymhs.backend.domain.content.client.TourApiException;
import com.jkymhs.backend.domain.content.client.TourApiItems;
import com.jkymhs.backend.domain.content.client.TourApiPage;
import com.jkymhs.backend.domain.content.client.TourApiProperties;
import com.jkymhs.backend.domain.content.client.TourApiQuotaExceededException;
import com.jkymhs.backend.domain.content.entity.BatchJobLog;
import com.jkymhs.backend.domain.content.entity.BatchJobName;
import com.jkymhs.backend.domain.content.entity.BatchJobStatus;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.entity.TourSyncCursorName;
import com.jkymhs.backend.domain.content.service.BatchJobLogService;
import com.jkymhs.backend.domain.content.service.ContentLoadContext;
import com.jkymhs.backend.domain.content.service.ContentRedisKeys;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSource;
import com.jkymhs.backend.domain.content.service.EventPeriod;
import com.jkymhs.backend.domain.content.service.IntroTarget;
import com.jkymhs.backend.domain.content.service.TourIntro;
import com.jkymhs.backend.domain.content.service.TourSyncCursorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class TourSyncJob {

    static final List<Integer> TOUR_TYPE_IDS = List.of(12, 14, 15, 32, 39);
    private static final int FESTIVAL_TYPE_ID = 15;
    private static final Duration INTRO_REFRESH_AFTER = Duration.ofDays(30);
    private static final int FESTIVAL_LOOKBACK_DAYS = 365;
    private static final int PURGE_BATCH = 500;

    private final TourApiClient tourApiClient;
    private final TourApiProperties properties;
    private final ContentService contentService;
    private final TourSyncCursorService cursorService;
    private final BatchJobLogService batchJobLogService;
    private final BatchAlertNotifier alertNotifier;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public TourSyncJob(TourApiClient tourApiClient, TourApiProperties properties, ContentService contentService,
                       TourSyncCursorService cursorService, BatchJobLogService batchJobLogService,
                       BatchAlertNotifier alertNotifier, StringRedisTemplate redisTemplate, Clock clock) {
        this.tourApiClient = tourApiClient;
        this.properties = properties;
        this.contentService = contentService;
        this.cursorService = cursorService;
        this.batchJobLogService = batchJobLogService;
        this.alertNotifier = alertNotifier;
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    @Scheduled(cron = "${tour-api.sync.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void scheduledRun() {
        run();
    }

    public TourSyncResult run() {
        if (!tourApiClient.isConfigured()) {
            log.warn("TourAPI 서비스 키(TOUR_API_KEY)가 없어 동기화를 건너뜁니다.");
            return TourSyncResult.notRun();
        }
        Optional<String> savedSince = cursorService.get(TourSyncCursorName.MODIFIED_SINCE);
        if (savedSince.isEmpty() && !contentService.hasAnyContent()) {
            log.warn("콘텐츠가 비어 있습니다. CSV 초기 적재 필요 (TOUR_IMPORT_CSV_DIR). 동기화를 건너뜁니다.");
            return TourSyncResult.notRun();
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate since = savedSince.map(LocalDate::parse)
                .orElseGet(() -> contentService.findMaxTourModifiedAt()
                        .map(LocalDateTime::toLocalDate)
                        .orElse(today.minusDays(1)));

        BatchJobLog jobLog = batchJobLogService.start(BatchJobName.TOUR_SYNC);
        Progress progress = new Progress();
        try {
            boolean completed = syncChanges(since, today, progress);
            if (completed) {
                cursorService.set(TourSyncCursorName.MODIFIED_SINCE, today.toString());
                completed = backfillIntro(progress);
            }
            purgeCandidateCache();
            BatchJobStatus status = completed && progress.failed == 0 ? BatchJobStatus.SUCCESS : BatchJobStatus.PARTIAL;
            String note = completed ? null : "TourAPI 일일 호출 한도 초과로 중단 (다음 실행에서 이어감)";
            batchJobLogService.finish(jobLog, status, progress.processed(), progress.failed, progress.apiCalls, note);
            log.info("TourAPI sync {}: upserted={} deleted={} skipped={} intro={} failed={} calls={}", status,
                    progress.upserted, progress.deleted, progress.skipped, progress.intro, progress.failed, progress.apiCalls);
            return progress.toResult(status);
        } catch (RuntimeException e) {
            log.error("TourAPI sync failed", e);
            batchJobLogService.finish(jobLog, BatchJobStatus.FAILED, progress.processed(), progress.failed,
                    progress.apiCalls, e.getClass().getSimpleName() + ": " + e.getMessage());
            alertNotifier.notifyFailure("[TOUR_SYNC] TourAPI 동기화 실패: " + e.getMessage());
            return progress.toResult(BatchJobStatus.FAILED);
        }
    }

    private boolean syncChanges(LocalDate since, LocalDate today, Progress progress) {
        ContentLoadContext context = contentService.loadContext();
        try {
            Map<Long, EventPeriod> festivalPeriods = fetchFestivalPeriods(since, today, progress);
            for (int typeId : TOUR_TYPE_IDS) {
                int pageNo = 1;
                TourApiPage page;
                do {
                    page = tourApiClient.areaBasedSyncList(typeId, since, pageNo++);
                    progress.apiCalls++;
                    applyPage(page.items(), typeId == FESTIVAL_TYPE_ID ? festivalPeriods : Map.of(), context, progress);
                } while (page.hasNext());
            }
            return true;
        } catch (TourApiQuotaExceededException e) {
            log.warn("TourAPI quota exceeded during change sync: {}", e.getMessage());
            return false;
        }
    }

    private Map<Long, EventPeriod> fetchFestivalPeriods(LocalDate since, LocalDate today, Progress progress) {
        Map<Long, EventPeriod> periods = new HashMap<>();
        LocalDate eventStart = today.minusDays(FESTIVAL_LOOKBACK_DAYS);
        int pageNo = 1;
        TourApiPage page;
        do {
            page = tourApiClient.searchFestival(eventStart, since, pageNo++);
            progress.apiCalls++;
            for (Map<String, String> item : page.items()) {
                ContentSource source = TourApiItems.toContentSource(item);
                if (source != null && source.eventStartDate() != null && source.eventEndDate() != null) {
                    periods.put(source.id(), new EventPeriod(source.eventStartDate(), source.eventEndDate()));
                }
            }
        } while (page.hasNext());
        return periods;
    }

    private void applyPage(List<Map<String, String>> items, Map<Long, EventPeriod> fetchedPeriods,
                           ContentLoadContext context, Progress progress) {
        List<Long> hiddenIds = new ArrayList<>();
        List<ContentSource> sources = new ArrayList<>();
        List<Long> festivalsWithoutPeriod = new ArrayList<>();
        for (Map<String, String> item : items) {
            if (TourApiItems.isHidden(item)) {
                Long id = TourApiItems.contentId(item);
                if (id != null) {
                    hiddenIds.add(id);
                }
                continue;
            }
            ContentSource source = TourApiItems.toContentSource(item);
            if (source == null) {
                progress.skipped++;
                continue;
            }
            if (source.contentType() == ContentType.FESTIVAL && source.eventStartDate() == null) {
                EventPeriod period = fetchedPeriods.get(source.id());
                if (period != null) {
                    source = source.withEventDates(period.start(), period.end());
                } else {
                    festivalsWithoutPeriod.add(source.id());
                }
            }
            sources.add(source);
        }
        Map<Long, EventPeriod> storedPeriods = contentService.findFestivalPeriods(festivalsWithoutPeriod);
        List<ContentSource> loadable = new ArrayList<>();
        for (ContentSource source : sources) {
            EventPeriod stored = storedPeriods.get(source.id());
            if (stored != null && source.eventStartDate() == null) {
                source = source.withEventDates(stored.start(), stored.end());
            }
            if (source.skipReason() == null) {
                loadable.add(source);
            } else {
                progress.skipped++;
            }
        }
        progress.upserted += contentService.upsert(loadable, context);
        progress.deleted += contentService.markDeleted(hiddenIds);
    }

    private boolean backfillIntro(Progress progress) {
        LocalDateTime before = LocalDateTime.now(clock).minus(INTRO_REFRESH_AFTER);
        List<IntroTarget> targets = contentService.findIntroTargets(properties.sync().introPerRun(), before);
        Long lastId = null;
        try {
            for (IntroTarget target : targets) {
                try {
                    Map<String, String> item = tourApiClient.detailIntro(target.contentId(), target.tourContentTypeId());
                    progress.apiCalls++;
                    TourIntro intro = TourApiItems.toIntro(item);
                    contentService.applyIntro(target.contentId(), intro != null ? intro : emptyIntro());
                    progress.intro++;
                    lastId = target.contentId();
                } catch (TourApiQuotaExceededException e) {
                    throw e;
                } catch (TourApiException e) {
                    progress.apiCalls++;
                    progress.failed++;
                    log.warn("detailIntro2 failed for {}: {}", target.contentId(), e.getMessage());
                }
            }
            return true;
        } catch (TourApiQuotaExceededException e) {
            log.warn("TourAPI quota exceeded during intro backfill: {}", e.getMessage());
            return false;
        } finally {
            if (lastId != null) {
                cursorService.set(TourSyncCursorName.INTRO_LAST_ID, lastId.toString());
            }
        }
    }

    private void purgeCandidateCache() {
        ScanOptions options = ScanOptions.scanOptions().match(ContentRedisKeys.candidatesPattern()).count(PURGE_BATCH).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            List<String> keys = new ArrayList<>();
            while (cursor.hasNext()) {
                keys.add(cursor.next());
                if (keys.size() >= PURGE_BATCH) {
                    redisTemplate.delete(keys);
                    keys.clear();
                }
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (DataAccessException e) {
            log.warn("Failed to purge recommendation candidate cache: {}", e.getMessage());
        }
    }

    private static TourIntro emptyIntro() {
        return new TourIntro(null, null, null, null, null, null, null, null, null, null);
    }

    private static final class Progress {
        private int upserted;
        private int deleted;
        private int skipped;
        private int intro;
        private int failed;
        private int apiCalls;

        int processed() {
            return upserted + deleted + intro;
        }

        TourSyncResult toResult(BatchJobStatus status) {
            return new TourSyncResult(status, upserted, deleted, skipped, intro, failed, apiCalls);
        }
    }
}
