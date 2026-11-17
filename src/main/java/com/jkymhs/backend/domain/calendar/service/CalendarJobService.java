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

/**
 * 캘린더 작업 만들기·조회의 공통 부분. 업로드·자동 동기화·해제가 모두 여기서 작업 행을 넣는다.
 *
 * 작업을 만들면 같은 트랜잭션에서 그 항목들을 PENDING으로 바꾼다 (화면에 "올리는 중"으로 보이게).
 * 작업 행과 항목 상태가 같이 커밋·롤백되므로 "작업은 없는데 PENDING인 항목"이 생기지 않는다.
 */
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

    // ---------- 만들기 ----------

    @Transactional
    public CalendarSyncJob create(Long userId, Long tripId, CalendarJobType type, String calendarId,
                                  List<CalendarJobOp> ops) {
        CalendarSyncJob job = jobRepository.save(CalendarSyncJob.create(userId, tripId, type, calendarId, ops.size(),
                LocalDateTime.now(clock)));
        saveItems(job, ops);
        return job;
    }

    /** 해제(모두 지우기)·탈퇴: 토큰 사본을 넣은 DELETE_ALL 작업 */
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

    // ---------- 확인 ----------

    /**
     * 업로드처럼 구글에 쓸 작업을 만들 수 있는 연동 (ACTIVE). 같은 회원의 요청끼리 한 줄로 서도록 연동 행을 잠근다.
     *
     * @throws CustomException 403 CALENDAR_NOT_LINKED — 연동이 없거나 만료(REVOKED)
     */
    @Transactional
    public GoogleCalendarLink activeLinkForUpdate(Long userId) {
        return linkRepository.findByUserIdForUpdate(userId).filter(GoogleCalendarLink::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.CALENDAR_NOT_LINKED, "연동 없음 또는 만료"));
    }

    /**
     * 사용자가 누른 작업(업로드·일괄·실패만 다시)은 회원당 하나만 돈다. 같은 일정을 두 작업이 동시에 고치면
     * 순서가 꼬여서(이동 중에 수정 등) 결과를 알 수 없기 때문이다. FE는 description의 작업 id로 그 작업 진행을 보여준다.
     *
     * @throws CustomException 409 CALENDAR_SYNC_IN_PROGRESS
     */
    public void ensureNoActiveUserJob(Long userId) {
        jobRepository.findFirstByUserIdAndJobTypeInAndStatusInOrderByIdAsc(userId, CalendarJobType.USER_INITIATED,
                CalendarJobStatus.ACTIVE).ifPresent(job -> {
            throw new CustomException(ErrorCode.CALENDAR_SYNC_IN_PROGRESS, ErrorCode.CALENDAR_SYNC_IN_PROGRESS.getMessage(),
                    String.valueOf(job.getId()));
        });
    }

    /** 내 작업. 다른 회원의 작업도 404다 (작업 id로 남의 작업이 있는지 알 수 없게) */
    public CalendarSyncJob ownedJob(Long userId, Long jobId) {
        return jobRepository.findById(jobId).filter(job -> job.getUserId().equals(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, JOB_NOT_FOUND,
                        "없거나 다른 회원의 작업: " + jobId));
    }

    // ---------- 진행 상태 ----------

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

        // 여행별 n/m (일괄 업로드 결과 화면)
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
