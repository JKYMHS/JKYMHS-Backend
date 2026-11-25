package com.jkymhs.backend.domain.calendar.worker;

import com.jkymhs.backend.domain.calendar.client.google.GoogleCalendarProperties;
import com.jkymhs.backend.domain.calendar.entity.CalendarFailureReason;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobItemStatus;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobOperation;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobStatus;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobType;
import com.jkymhs.backend.domain.calendar.entity.CalendarSyncJob;
import com.jkymhs.backend.domain.calendar.entity.CalendarSyncJobItem;
import com.jkymhs.backend.domain.calendar.entity.GoogleCalendarLink;
import com.jkymhs.backend.domain.calendar.repository.CalendarSyncJobItemRepository;
import com.jkymhs.backend.domain.calendar.repository.CalendarSyncJobRepository;
import com.jkymhs.backend.domain.calendar.repository.GoogleCalendarLinkRepository;
import com.jkymhs.backend.domain.trip.service.CalendarItem;
import com.jkymhs.backend.domain.trip.service.TripCalendarSupport;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Transactional
public class CalendarJobStore {
    private static final int MAX_BACKOFF_EXPONENT = 6;

    private final CalendarSyncJobRepository jobRepository;
    private final CalendarSyncJobItemRepository jobItemRepository;
    private final GoogleCalendarLinkRepository linkRepository;
    private final TripCalendarSupport tripCalendarSupport;
    private final GoogleCalendarProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public record Round(Long jobId, Long userId, String encryptedRefreshToken, List<CalendarSyncJobItem> items) {
    }

    public record Finished(Long userId, CalendarJobStatus status, boolean revokeAfter, String encryptedRefreshToken,
                           List<Long> tripIds) {
    }

    public List<Long> claimDue(int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<CalendarSyncJob> jobs = jobRepository.findDueForUpdate(now, limit);
        jobs.forEach(job -> job.claim(now.plus(properties.lease())));
        return jobs.stream().map(CalendarSyncJob::getId).toList();
    }

    public void release(Long jobId) {
        jobRepository.findById(jobId).ifPresent(job -> job.release(LocalDateTime.now(clock)));
    }

    @Transactional(readOnly = true)
    public Optional<Round> round(Long jobId) {
        return jobRepository.findById(jobId).filter(job -> job.getStatus() == CalendarJobStatus.RUNNING)
                .map(job -> new Round(job.getId(), job.getUserId(), job.getEncryptedRefreshToken(),
                        jobItemRepository.findAllByJobIdAndStatusOrderByIdAsc(job.getId(), CalendarJobItemStatus.PENDING,
                                PageRequest.of(0, properties.batchSize()))));
    }

    @Transactional(readOnly = true)
    public Optional<String> activeLinkToken(Long userId) {
        return linkRepository.findByUserId(userId).filter(GoogleCalendarLink::isActive)
                .map(GoogleCalendarLink::getEncryptedRefreshToken);
    }

    public void revokeLink(Long userId, String snapshotToken) {
        linkRepository.findByUserIdForUpdate(userId)
                .filter(link -> snapshotToken == null || snapshotToken.equals(link.getEncryptedRefreshToken()))
                .ifPresent(GoogleCalendarLink::revoke);
    }

    public void succeeded(Long userId, Long jobItemId, String calendarId, String eventId, CalendarItem planItem,
                          boolean firstCreate) {
        CalendarSyncJobItem item = jobItemRepository.findById(jobItemId).orElseThrow();
        item.done();
        if (item.getPlanItemId() == null) {
            return;
        }
        if (item.getOperation() == CalendarJobOperation.DELETE) {
            tripCalendarSupport.clearEvent(item.getPlanItemId(), calendarId, eventId);
            return;
        }
        if (planItem != null) {
            tripCalendarSupport.markSynced(item.getPlanItemId(), calendarId, eventId);
            if (firstCreate && planItem.contentId() != null) {
                eventPublisher.publishEvent(new BehaviorSignalEvent(userId, planItem.contentId(), BehaviorSignalType.CALENDAR, planItem.companion(), planItem.tripId(),
                        LocalDateTime.now(clock)));
            }
        }
    }

    public void failed(Long jobItemId, CalendarFailureReason reason) {
        CalendarSyncJobItem item = jobItemRepository.findById(jobItemId).orElseThrow();
        item.fail(reason);
        markPlanItemFailed(item);
    }

    public void retryLater(Long jobId, Long jobItemId, String error) {
        CalendarSyncJobItem item = jobItemRepository.findById(jobItemId).orElseThrow();
        if (!item.retryLater(properties.maxAttempts())) {
            markPlanItemFailed(item);
        }
        Duration delay = backoffDelay(item.getAttemptCount() - 1);
        jobRepository.findById(jobId).ifPresent(job -> job.delay(LocalDateTime.now(clock).plus(delay), error));
    }

    public void failPending(Long jobId, CalendarFailureReason reason) {
        jobItemRepository.findAllByJobIdAndStatus(jobId, CalendarJobItemStatus.PENDING).forEach(item -> {
            item.fail(reason);
            markPlanItemFailed(item);
        });
    }

    private void markPlanItemFailed(CalendarSyncJobItem item) {
        if (item.getStatus() == CalendarJobItemStatus.FAILED && item.getPlanItemId() != null
                && item.getOperation() != CalendarJobOperation.DELETE) {
            tripCalendarSupport.markFailed(item.getPlanItemId());
        }
    }

    public boolean backoffOrGiveUp(Long jobId, String error) {
        CalendarSyncJob job = jobRepository.findById(jobId).orElseThrow();
        if (job.getAttemptCount() + 1 >= properties.maxAttempts()) {
            job.finish(job.getStatus(), job.getSucceeded(), error);
            failPending(jobId, CalendarFailureReason.GOOGLE_ERROR);
            return true;
        }
        job.backoff(LocalDateTime.now(clock).plus(backoffDelay(job.getAttemptCount())), error);
        return false;
    }

    @Transactional(readOnly = true)
    public boolean hasPending(Long jobId) {
        return jobItemRepository.countByJobIdAndStatus(jobId, CalendarJobItemStatus.PENDING) > 0;
    }

    public void continueNow(Long jobId) {
        jobRepository.findById(jobId).ifPresent(job -> job.release(LocalDateTime.now(clock)));
    }

    public Finished finish(Long jobId) {
        CalendarSyncJob job = jobRepository.findById(jobId).orElseThrow();
        List<CalendarSyncJobItem> items = jobItemRepository.findAllByJobIdOrderByIdAsc(jobId);
        int done = (int) items.stream().filter(item -> item.getStatus() == CalendarJobItemStatus.DONE).count();
        int failed = (int) items.stream().filter(item -> item.getStatus() == CalendarJobItemStatus.FAILED).count();
        CalendarJobStatus status = failed == 0 ? CalendarJobStatus.DONE
                : done == 0 ? CalendarJobStatus.FAILED : CalendarJobStatus.PARTIAL_FAILED;
        job.finish(status, done, null);
        Set<Long> tripIds = new LinkedHashSet<>();
        if (job.getTripId() != null) {
            tripIds.add(job.getTripId());
        }
        items.stream().map(CalendarSyncJobItem::getTripId).filter(Objects::nonNull).forEach(tripIds::add);
        return new Finished(job.getUserId(), status, job.isRevokeAfter(), job.getEncryptedRefreshToken(),
                job.getJobType() == CalendarJobType.DELETE_ALL ? List.of() : List.copyOf(tripIds));
    }

    public boolean removeLinkIfSameToken(Long userId, String snapshotToken) {
        Optional<GoogleCalendarLink> link = linkRepository.findByUserIdForUpdate(userId);
        if (link.isEmpty()) {
            return true;
        }
        if (!link.get().getEncryptedRefreshToken().equals(snapshotToken)) {
            return false;
        }
        linkRepository.deleteByUserIdInBulk(userId);
        tripCalendarSupport.clearAll(userId);
        return true;
    }

    private Duration backoffDelay(int exponent) {
        return properties.backoffBase().multipliedBy(1L << Math.max(0, Math.min(exponent, MAX_BACKOFF_EXPONENT)));
    }
}
