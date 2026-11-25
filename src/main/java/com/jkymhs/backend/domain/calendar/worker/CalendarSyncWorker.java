package com.jkymhs.backend.domain.calendar.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

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
