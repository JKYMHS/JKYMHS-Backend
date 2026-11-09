package com.jkymhs.backend.domain.popularity;

import com.jkymhs.backend.domain.popularity.service.PopularityScores;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PopularityScoresTest {

    @Test
    @DisplayName("가장 높은 원점수가 1000점이고 나머지는 로그 비율이다")
    void logScale() {
        Map<Long, Integer> scores = PopularityScores.normalize(Map.of(1L, 99L, 2L, 9L, 3L, 0L));

        assertThat(scores).containsEntry(1L, 1000).containsEntry(2L, 500).doesNotContainKey(3L);
    }

    @Test
    @DisplayName("행동이 하나도 없으면 빈 결과다 (모든 콘텐츠가 0점)")
    void empty() {
        assertThat(PopularityScores.normalize(Map.of())).isEmpty();
        assertThat(PopularityScores.normalize(Map.of(1L, 0L))).isEmpty();
    }
}
