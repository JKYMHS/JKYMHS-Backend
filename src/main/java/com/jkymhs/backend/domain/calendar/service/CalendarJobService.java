package com.jkymhs.backend.domain.calendar.service;

import com.jkymhs.backend.domain.calendar.dto.response.CalendarJobStatusResponse;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobItemStatus;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobStatus;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobType;
import com.jkymhs.backend.domain.calendar.entity.CalendarSyncJob;
import com.jkymhs.backend.domain.calendar.entity.CalendarSyncJobItem;
import com.jkymhs.backend.domain.calendar.entity.GoogleCalendarLink;
import com.jkymhs.backend.domain.calendar.repository.CalendarSyncJobItemRepository;
import com.jkymhs.backend.domain.calendar.repository.CalendarSyncJobRepository;
import com.jkymhs.backend.domain.calendar.repository.GoogleCalendarLinkRepository;
import com.jkymhs.backend.domain.trip.service.TripCalendarSupport;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarJobService {
    public static final int POLL_AFTER_MS = 2000;
    static final String JOB_NOT_FOUND = "존재하지 않는 작업입니다.";

    private final CalendarSyncJobRepository jobRepository;
    private final CalendarSyncJobItemRepository jobItemRepository;
    private final GoogleCalendarLinkRepository linkRepository;
    private final TripCalendarSupport tripCalendarSupport;
    private final Clock clock;

    @Transactional
    public CalendarSyncJob create(Long userId, Long tripId, CalendarJobType type, String calendarId,
                                  List<CalendarJobOp> ops) {
        CalendarSyncJob job = jobRepository.save(CalendarSyncJob.create(userId, tripId, type, calendarId, ops.size(),
                LocalDateTime.now(clock)));
        saveItems(job, ops);
        return job;
    }

    @Transactional
    public CalendarSyncJob createDeleteAll(Long userId, String encryptedRefreshToken, List<CalendarJobOp> ops) {
        CalendarSyncJob job = jobRepository.save(CalendarSyncJob.deleteAll(userId, ops.size(), encryptedRefreshToken,
                LocalDateTime.now(clock)));
        saveItems(job, ops);
        return job;
    }

    private void saveItems(CalendarSyncJob job, List<CalendarJobOp> ops) {
        jobItemRepository.saveAll(ops.stream().map(op -> CalendarSyncJobItem.create(job.getId(), op.planItemId(),
                op.tripId(), op.operation(), op.eventId(), op.calendarId(), op.itemName(), op.startLabel())).toList());
        tripCalendarSupport.markPending(ops.stream().filter(CalendarJobOp::writesPlanItem)
                .map(CalendarJobOp::planItemId).distinct().toList());
    }

    @Transactional
    public GoogleCalendarLink activeLinkForUpdate(Long userId) {
        return linkRepository.findByUserIdForUpdate(userId).filter(GoogleCalendarLink::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.CALENDAR_NOT_LINKED, "연동 없음 또는 만료"));
    }

    public void ensureNoActiveUserJob(Long userId) {
        jobRepository.findFirstByUserIdAndJobTypeInAndStatusInOrderByIdAsc(userId, CalendarJobType.USER_INITIATED,
                CalendarJobStatus.ACTIVE).ifPresent(job -> {
            throw new CustomException(ErrorCode.CALENDAR_SYNC_IN_PROGRESS, ErrorCode.CALENDAR_SYNC_IN_PROGRESS.getMessage(),
                    String.valueOf(job.getId()));
        });
    }

    public CalendarSyncJob ownedJob(Long userId, Long jobId) {
        return jobRepository.findById(jobId).filter(job -> job.getUserId().equals(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, JOB_NOT_FOUND,
                        "없거나 다른 회원의 작업: " + jobId));
    }

    public CalendarJobStatusResponse status(Long userId, Long jobId) {
        CalendarSyncJob job = ownedJob(userId, jobId);
        List<CalendarSyncJobItem> items = jobItemRepository.findAllByJobIdOrderByIdAsc(jobId);

        List<CalendarJobStatusResponse.FailedItem> failedItems = items.stream()
                .filter(item -> item.getStatus() == CalendarJobItemStatus.FAILED)
                .map(item -> new CalendarJobStatusResponse.FailedItem(item.getPlanItemId(), item.getTripId(),
                        item.getItemName(), item.getStartLabel(), item.getReasonCode(),
                        item.getReasonCode() == null ? null : item.getReasonCode().reason()))
                .toList();
        int succeeded = (int) items.stream().filter(item -> item.getStatus() == CalendarJobItemStatus.DONE).count();

        Map<Long, int[]> byTrip = new LinkedHashMap<>();
        items.stream().filter(item -> item.getTripId() != null).forEach(item -> {
            int[] counts = byTrip.computeIfAbsent(item.getTripId(), id -> new int[2]);
            counts[1]++;
            if (item.getStatus() == CalendarJobItemStatus.DONE) {
                counts[0]++;
            }
        });
        Map<Long, String> titles = tripCalendarSupport.titles(byTrip.keySet());
        List<CalendarJobStatusResponse.Trip> trips = new ArrayList<>();
        byTrip.forEach((tripId, counts) ->
                trips.add(new CalendarJobStatusResponse.Trip(tripId, titles.get(tripId), counts[0], counts[1])));

        boolean terminal = job.getStatus().terminal();
        boolean retryable = terminal && !failedItems.isEmpty();
        return new CalendarJobStatusResponse(job.getId(), job.getJobType(), job.getStatus(), job.getTotal(), succeeded,
                failedItems.size(), failedItems, trips, retryable, terminal ? null : POLL_AFTER_MS);
    }
}
