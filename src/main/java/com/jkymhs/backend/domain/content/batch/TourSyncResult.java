package com.jkymhs.backend.domain.content.batch;

import com.jkymhs.backend.domain.content.entity.BatchJobStatus;

public record TourSyncResult(BatchJobStatus status, int upserted, int deleted, int skipped, int introUpdated,
                             int failed, int apiCalls) {

    static TourSyncResult notRun() {
        return new TourSyncResult(null, 0, 0, 0, 0, 0, 0);
    }
}
