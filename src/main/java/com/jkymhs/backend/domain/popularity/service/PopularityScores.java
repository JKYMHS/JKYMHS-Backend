package com.jkymhs.backend.domain.popularity.service;

import java.util.HashMap;
import java.util.Map;

public final class PopularityScores {
    public static final int MAX_SCORE = 1000;

    private PopularityScores() {
    }

    public static Map<Long, Integer> normalize(Map<Long, Long> raw) {
        long maxRaw = raw.values().stream().mapToLong(Long::longValue).max().orElse(0);
        Map<Long, Integer> scores = new HashMap<>();
        if (maxRaw <= 0) {
            return scores;
        }
        double denominator = Math.log1p(maxRaw);
        raw.forEach((id, value) -> {
            if (value > 0) {
                scores.put(id, (int) Math.round(MAX_SCORE * Math.log1p(value) / denominator));
            }
        });
        return scores;
    }
}
