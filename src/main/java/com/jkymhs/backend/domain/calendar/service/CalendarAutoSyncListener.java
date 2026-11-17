package com.jkymhs.backend.domain.calendar.service;

import com.jkymhs.backend.domain.trip.event.CalendarEventsOrphanedEvent;
import com.jkymhs.backend.domain.trip.event.ConfirmedPlanChangedEvent;
import com.jkymhs.backend.domain.trip.event.ConfirmedPlanSwitchedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 여행 도메인 이벤트 → 캘린더 작업.
 *
 * - 확정안 내용 변경: 저장이 커밋된 뒤(AFTER_COMMIT) 새 트랜잭션에서 SYNC 작업을 넣는다. 저장 트랜잭션 안에서 하면
 *   일정 항목의 최신 값이 아직 커밋 전이라 작업자가 옛 값을 읽을 수 있고, 저장이 롤백돼도 작업이 남을 수 있다.
 *   이 단계의 예외는 이미 커밋된 저장 요청을 500으로 만들기 때문에 잡아서 로그만 남긴다 (다음 변경·업로드 때 다시 맞춰진다).
 * - 확정안 교체·일정 삭제: 행이 지워지기 전에, 같은 트랜잭션 안에서(동기) 작업 행을 넣는다.
 *   지운 뒤에는 어느 일정을 지워야 하는지 알 수 없고, 삭제가 롤백되면 작업도 같이 사라져야 하기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarAutoSyncListener {

    private final CalendarAutoSyncService autoSyncService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onConfirmedPlanChanged(ConfirmedPlanChangedEvent event) {
        try {
            autoSyncService.syncTrip(event.userId(), event.tripId());
        } catch (RuntimeException e) {
            log.error("캘린더 자동 동기화 작업을 만들지 못함: trip {}", event.tripId(), e);
        }
    }

    @EventListener
    public void onConfirmedPlanSwitched(ConfirmedPlanSwitchedEvent event) {
        autoSyncService.switchPlan(event.userId(), event.tripId(), event.newPlanId(), event.oldEvents());
    }

    @EventListener
    public void onEventsOrphaned(CalendarEventsOrphanedEvent event) {
        autoSyncService.deleteEvents(event.userId(), event.tripId(), event.events());
    }
}
