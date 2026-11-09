package com.jkymhs.backend.domain.calendar.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 캘린더 작업자. 2초마다(google-calendar.poll-interval) 차례가 된 작업을 최대 5개 집어서 calendarExecutor에 맡긴다.
 *
 * 집는 쿼리는 FOR UPDATE SKIP LOCKED라서 서버가 여러 대여도 같은 작업을 두 서버가 처리하지 않는다.
 * 집은 작업은 RUNNING + 임대 시간(5분)을 받는다. 작업자가 처리 중에 죽으면 임대가 끝난 뒤 다시 집힌다.
 *
 * 테스트는 @Scheduled를 기다리지 않고 {@link #runOnce()}를 직접 부른다 (G16d). 테스트의 실행기는 동기라서 runOnce가 끝나면 한 차례가 끝나 있다.
 */
@Slf4j
@Component
public class CalendarSyncWorker {

    private static final int CLAIM_LIMIT = 5;

    private final CalendarJobStore store;
    private final CalendarJobProcessor processor;
    private final TaskExecutor calendarExecutor;

    public CalendarSyncWorker(CalendarJobStore store, CalendarJobProcessor processor,
                              @Qualifier("calendarExecutor") TaskExecutor calendarExecutor) {
        this.store = store;
        this.processor = processor;
        this.calendarExecutor = calendarExecutor;
    }

    @Scheduled(fixedDelayString = "${google-calendar.poll-interval:2s}")
    public void poll() {
        runOnce();
    }

    /** @return 이번에 집은 작업 수 */
    public int runOnce() {
        List<Long> jobIds = store.claimDue(CLAIM_LIMIT);
        for (Long jobId : jobIds) {
            try {
                calendarExecutor.execute(() -> processor.process(jobId));
            } catch (TaskRejectedException e) {
                log.warn("캘린더 실행기가 가득 차서 작업 {}을 다음 차례로 미룸", jobId);
                store.release(jobId);
            }
        }
        return jobIds.size();
    }
}
