package com.jkymhs.backend.domain.content.batch;

public record TourImportResult(int regions, int classifications, int contents, int skippedContents,
                               int details, int skippedDetails) {
}
