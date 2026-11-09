package com.jkymhs.backend.domain.popularity.service;

import java.util.HashMap;
import java.util.Map;

/**
 * 원점수 → 0~1000 인기 점수.
 *
 * score = round(1000 × ln(1 + raw) / ln(1 + maxRaw)). 로그를 쓰는 이유: 조회가 몰리는 몇몇 콘텐츠(경복궁 등)가
 * 점수를 독차지하면 나머지가 모두 0 근처로 눌린다. 로그로 펴면 "800점 이상" 칩에 들어갈 콘텐츠가 적당히 생긴다 (§5 Q9).
 * 가장 높은 콘텐츠는 항상 1000점이다 (최댓값 기준 상대 점수).
 */
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
