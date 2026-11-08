package com.jkymhs.backend.domain.content.entity;

/**
 * batch_job_logs.job_name. 다른 도메인 배치(인기 점수 D3, 방문 확인 메일 D4)도 같은 로그 테이블을 쓰도록 여기서 한 번에 정한다.
 */
public enum BatchJobName {
    TOUR_SYNC,
    TOUR_IMPORT,
    POPULARITY,
    VISIT_CHECK_MAIL
}
