package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.entity.BatchJobLog;
import com.jkymhs.backend.domain.content.entity.BatchJobName;
import com.jkymhs.backend.domain.content.entity.BatchJobStatus;
import com.jkymhs.backend.domain.content.repository.BatchJobLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BatchJobLogService {

    private final BatchJobLogRepository batchJobLogRepository;
    private final Clock clock;

    // 배치가 롤백돼도 실패 기록은 남도록 별도 트랜잭션
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchJobLog start(BatchJobName name) {
        return batchJobLogRepository.save(BatchJobLog.start(name, LocalDateTime.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchJobLog finish(BatchJobLog log, BatchJobStatus status, int processed, int failed, int apiCalls,
                              String error) {
        BatchJobLog managed = batchJobLogRepository.findById(log.getId()).orElseThrow();
        managed.finish(status, processed, failed, apiCalls, error, LocalDateTime.now(clock));
        return managed;
    }

    public List<BatchJobLog> findRecent(BatchJobName name) {
        return batchJobLogRepository.findAllByJobNameOrderByStartedAtDescIdDesc(name);
    }
}
