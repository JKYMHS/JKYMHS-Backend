package com.jkymhs.backend.domain.behavior.service;

import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BehaviorSignalListener {

    private final BehaviorRecorder behaviorRecorder;

    @Async(BehaviorConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(BehaviorSignalEvent event) {
        behaviorRecorder.record(event);
    }
}
