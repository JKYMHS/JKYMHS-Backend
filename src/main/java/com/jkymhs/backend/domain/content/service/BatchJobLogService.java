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

/**
 * 배치 실행 기록. 모든 배치(TourAPI 동기화·CSV 적재, 인기 점수 D3, 방문 확인 메일 D4)가 시작할 때 {@link #start}, 끝날 때 {@link #finish}를 부른다.
 *
 * 기록은 배치 작업과 다른 트랜잭션(REQUIRES_NEW)으로 바로 커밋한다. 배치가 자기 트랜잭션 안에서 부르더라도
 * 배치가 실패해서 롤백될 때 "실패했다"는 기록까지 같이 사라지면 안 되기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BatchJobLogService {

    private final BatchJobLogRepository batchJobLogRepository;
    private final Clock clock;

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

    /** 최근 실행부터 */
    public List<BatchJobLog> findRecent(BatchJobName name) {
        return batchJobLogRepository.findAllByJobNameOrderByStartedAtDescIdDesc(name);
    }
}
