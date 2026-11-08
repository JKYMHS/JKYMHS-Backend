package com.jkymhs.backend.domain.content.batch;

import com.jkymhs.backend.domain.content.entity.BatchJobStatus;

/**
 * 동기화 한 번의 결과.
 *
 * @param status   null이면 실행하지 않음 (키 없음, 초기 적재 전)
 * @param upserted 넣거나 고친 콘텐츠 수
 * @param deleted  새로 삭제 표시한 수 (showflag=0)
 * @param skipped  좌표·축제 기간이 없거나 다루지 않는 종류라 건너뛴 수
 * @param introUpdated 운영 정보를 새로 받은 수
 * @param failed   운영 정보 호출이 실패한 수
 * @param apiCalls 이번 실행의 TourAPI 호출 수
 */
public record TourSyncResult(BatchJobStatus status, int upserted, int deleted, int skipped, int introUpdated,
                             int failed, int apiCalls) {

    static TourSyncResult notRun() {
        return new TourSyncResult(null, 0, 0, 0, 0, 0, 0);
    }
}
