package com.jkymhs.backend.domain.content.client;

import java.util.List;
import java.util.Map;

public record TourApiPage(List<Map<String, String>> items, int pageNo, int numOfRows, int totalCount) {

    public boolean hasNext() {
        return !items.isEmpty() && (long) pageNo * numOfRows < totalCount;
    }
}
