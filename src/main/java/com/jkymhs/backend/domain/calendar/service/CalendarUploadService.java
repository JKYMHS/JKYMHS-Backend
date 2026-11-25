package com.jkymhs.backend.domain.calendar.service;

import com.jkymhs.backend.domain.calendar.dto.request.CalendarBulkUploadRequest;
import com.jkymhs.backend.domain.calendar.dto.request.CalendarUploadRequest;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarBulkUploadResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarPreviewResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarRetryResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarUploadResponse;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobItemStatus;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobOperation;
import com.jkymhs.backend.domain.calendar.entity.CalendarJobType;
import com.jkymhs.backend.domain.calendar.entity.CalendarSyncJob;
import com.jkymhs.backend.domain.calendar.entity.CalendarSyncJobItem;
import com.jkymhs.backend.domain.calendar.entity.GoogleCalendarLink;
import com.jkymhs.backend.domain.calendar.repository.CalendarSyncJobItemRepository;
import com.jkymhs.backend.domain.calendar.repository.GoogleCalendarLinkRepository;
import com.jkymhs.backend.domain.trip.service.CalendarItem;
import com.jkymhs.backend.domain.trip.service.CalendarState;
import com.jkymhs.backend.domain.trip.service.CalendarTrip;
import com.jkymhs.backend.domain.trip.service.TripCalendarSupport;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarUploadService {
    static final int BULK_MAX_TRIPS = 10;
    private static final String BULK_LIMIT_MESSAGE = "한 번에 최대 " + BULK_MAX_TRIPS + "개 여행까지 올릴 수 있습니다.";
    private static final String ITEM_NOT_IN_PLAN = "확정 일정안에 없는 항목이 포함되어 있습니다.";
    private static final String UNSUPPORTED_JOB_TYPE = "지원하지 않는 작업 종류입니다.";
    private static final String NOTHING_TO_RETRY = "다시 올릴 항목이 없습니다.";

    private final TripCalendarSupport tripCalendarSupport;
    private final CalendarJobService jobService;
    private final GoogleCalendarLinkRepository linkRepository;
    private final CalendarSyncJobItemRepository jobItemRepository;

    public CalendarPreviewResponse preview(Long userId, Long tripId) {
        CalendarTrip trip = tripCalendarSupport.confirmedTrip(userId, tripId);
        String calendarId = StringUtils.hasText(trip.calendarId()) ? trip.calendarId()
                : linkRepository.findByUserId(userId).map(GoogleCalendarLink::getDefaultCalendarId)
                .orElse(GoogleCalendarLink.PRIMARY_CALENDAR);
        Map<Integer, List<CalendarItem>> byDay = trip.items().stream()
                .collect(Collectors.groupingBy(CalendarItem::dayNumber));
        List<CalendarPreviewResponse.Day> days = new ArrayList<>();
        for (int day = 1; day <= trip.days(); day++) {
            List<CalendarPreviewResponse.Item> items = byDay.getOrDefault(day, List.of()).stream()
                    .map(item -> new CalendarPreviewResponse.Item(item.itemId(), item.name(), item.startTime(),
                            item.endTime(), durationMinutes(item), item.allDay(), item.state()))
                    .toList();
            days.add(new CalendarPreviewResponse.Day(day, trip.startDate().plusDays(day - 1L), items));
        }
        return new CalendarPreviewResponse(trip.tripId(), trip.planId(), trip.planName(), calendarId, days);
    }

    private static Integer durationMinutes(CalendarItem item) {
        if (item.startTime() != null && item.endTime() != null) {
            long minutes = Duration.between(item.startTime(), item.endTime()).toMinutes();
            return (int) (minutes > 0 ? minutes : minutes + Duration.ofDays(1).toMinutes());
        }
        return item.stayMinutes();
    }

    @Transactional
    public CalendarUploadResponse upload(Long userId, Long tripId, CalendarUploadRequest request) {
        CalendarTrip trip = tripCalendarSupport.confirmedTrip(userId, tripId);
        List<CalendarItem> selected = select(trip, request.itemIds());
        GoogleCalendarLink link = jobService.activeLinkForUpdate(userId);
        jobService.ensureNoActiveUserJob(userId);

        String calendarId = firstText(request.calendarId(), trip.calendarId(), link.getDefaultCalendarId());
        List<CalendarJobOp> ops = opsFor(trip, selected, calendarId);
        if (!ops.isEmpty()) {
            tripCalendarSupport.useCalendar(tripId, calendarId);
        }
        CalendarSyncJob job = jobService.create(userId, tripId, CalendarJobType.UPLOAD, calendarId, ops);
        return new CalendarUploadResponse(job.getId(), ops.size(), CalendarJobService.POLL_AFTER_MS);
    }

    private static List<CalendarItem> select(CalendarTrip trip, List<Long> itemIds) {
        if (itemIds == null) {
            return trip.items().stream().filter(item -> item.state() != CalendarState.SYNCED).toList();
        }
        Map<Long, CalendarItem> byId = trip.items().stream()
                .collect(Collectors.toMap(CalendarItem::itemId, Function.identity()));
        Set<Long> unique = new HashSet<>();
        List<CalendarItem> selected = new ArrayList<>();
        for (Long itemId : itemIds) {
            CalendarItem item = byId.get(itemId);
            if (item == null) {
                throw new CustomException(ErrorCode.INVALID_INPUT, ITEM_NOT_IN_PLAN, "확정안에 없는 항목: " + itemId);
            }
            if (unique.add(itemId)) {
                selected.add(item);
            }
        }
        return selected;
    }

    private static List<CalendarJobOp> opsFor(CalendarTrip trip, List<CalendarItem> selected, String calendarId) {
        List<CalendarJobOp> ops = new ArrayList<>(selected.stream().map(item -> CalendarJobOp.upsert(item, calendarId))
                .toList());
        Set<Long> selectedIds = selected.stream().map(CalendarItem::itemId).collect(Collectors.toSet());
        trip.items().stream()
                .filter(item -> !selectedIds.contains(item.itemId()) && item.hasEvent()
                        && !calendarId.equals(item.calendarId()))
                .forEach(item -> ops.add(CalendarJobOp.upsert(item, calendarId)));
        return ops;
    }

    @Transactional
    public CalendarBulkUploadResponse bulkUpload(Long userId, CalendarBulkUploadRequest request) {
        if (request.type() != CalendarJobType.BULK_UPLOAD) {
            throw new CustomException(ErrorCode.INVALID_INPUT, UNSUPPORTED_JOB_TYPE, "작업 종류 " + request.type());
        }
        List<Long> tripIds = request.tripIds().stream().distinct().toList();
        if (tripIds.size() > BULK_MAX_TRIPS) {
            throw new CustomException(ErrorCode.SCHEDULE_LIMIT_EXCEEDED, BULK_LIMIT_MESSAGE, "여행 수 " + tripIds.size());
        }
        List<CalendarTrip> trips = tripIds.stream().map(id -> tripCalendarSupport.tripForBulk(userId, id)).toList();
        GoogleCalendarLink link = jobService.activeLinkForUpdate(userId);
        jobService.ensureNoActiveUserJob(userId);

        String calendarId = firstText(request.calendarId(), link.getDefaultCalendarId());
        List<CalendarJobOp> ops = new ArrayList<>();
        List<CalendarBulkUploadResponse.Trip> results = new ArrayList<>();
        for (CalendarTrip trip : trips) {
            if (!trip.confirmed()) {
                results.add(new CalendarBulkUploadResponse.Trip(trip.tripId(), 0, BulkSkipReason.NOT_CONFIRMED));
                continue;
            }
            List<CalendarJobOp> tripOps = opsFor(trip, select(trip, null), calendarId);
            if (tripOps.isEmpty()) {
                results.add(new CalendarBulkUploadResponse.Trip(trip.tripId(), 0, BulkSkipReason.ALREADY_SYNCED));
                continue;
            }
            tripCalendarSupport.useCalendar(trip.tripId(), calendarId);
            ops.addAll(tripOps);
            results.add(new CalendarBulkUploadResponse.Trip(trip.tripId(), tripOps.size(), null));
        }
        CalendarSyncJob job = jobService.create(userId, null, CalendarJobType.BULK_UPLOAD, calendarId, ops);
        return new CalendarBulkUploadResponse(job.getId(), results);
    }

    @Transactional
    public CalendarRetryResponse retryFailed(Long userId, Long jobId) {
        CalendarSyncJob source = jobService.ownedJob(userId, jobId);
        if (source.isActive()) {
            throw new CustomException(ErrorCode.CALENDAR_SYNC_IN_PROGRESS, ErrorCode.CALENDAR_SYNC_IN_PROGRESS.getMessage(),
                    String.valueOf(source.getId()));
        }
        List<CalendarJobOp> ops = new ArrayList<>();
        for (CalendarSyncJobItem failed : jobItemRepository.findAllByJobIdOrderByIdAsc(jobId)) {
            if (failed.getStatus() != CalendarJobItemStatus.FAILED) {
                continue;
            }
            if (failed.getOperation() == CalendarJobOperation.DELETE) {
                ops.add(new CalendarJobOp(failed.getPlanItemId(), failed.getTripId(), CalendarJobOperation.DELETE,
                        failed.getEventId(), failed.getCalendarId(), failed.getItemName(), failed.getStartLabel()));
                continue;
            }
            Optional.ofNullable(failed.getPlanItemId()).flatMap(tripCalendarSupport::item)
                    .ifPresent(item -> ops.add(CalendarJobOp.upsert(item, failed.getCalendarId())));
        }
        if (ops.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT, NOTHING_TO_RETRY, "작업 " + jobId + "에 다시 올릴 항목 없음");
        }
        jobService.activeLinkForUpdate(userId);
        jobService.ensureNoActiveUserJob(userId);
        CalendarSyncJob job = jobService.create(userId, source.getTripId(), source.getJobType(), source.getCalendarId(),
                ops);
        return new CalendarRetryResponse(job.getId());
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return GoogleCalendarLink.PRIMARY_CALENDAR;
    }
}
