package com.jkymhs.backend.domain.content.batch;

/**
 * CSV 적재 한 번의 결과.
 *
 * @param skippedContents 좌표·축제 기간이 없거나 형식이 틀려서 넣지 않은 줄
 * @param skippedDetails  contents에 없는 콘텐츠의 상세 줄
 */
public record TourImportResult(int regions, int classifications, int contents, int skippedContents,
                               int details, int skippedDetails) {
}
