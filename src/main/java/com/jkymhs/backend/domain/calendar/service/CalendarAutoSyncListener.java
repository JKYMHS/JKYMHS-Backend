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
