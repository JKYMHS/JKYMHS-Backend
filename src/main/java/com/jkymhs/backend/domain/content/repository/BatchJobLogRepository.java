package com.jkymhs.backend.domain.content.repository;

import com.jkymhs.backend.domain.content.entity.BatchJobLog;
import com.jkymhs.backend.domain.content.entity.BatchJobName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchJobLogRepository extends JpaRepository<BatchJobLog, Long> {

    List<BatchJobLog> findAllByJobNameOrderByStartedAtDescIdDesc(BatchJobName jobName);
}
