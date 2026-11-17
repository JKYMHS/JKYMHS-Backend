package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.RecommendationTestSupport;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequest;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationCleanupJobTest extends RecommendationTestSupport {

    @Autowired
    private RecommendationCleanupJob cleanupJob;

    @Test
    @DisplayName("10분 넘은 PENDING은 FAILED(INTERNAL)가 되고, 24시간 지난 요청은 출발지 좌표·주소가 지워진다")
    void cleanup() {
        User user = saveUser("clean@example.com");
        LocalDateTime start = LocalDateTime.now(clock);
        RecommendationRequest stale = insertRequest(user.getId(), RecommendationStatus.PENDING, start);
        RecommendationRequest done = insertRequest(user.getId(), RecommendationStatus.DONE, start);

        clock.advance(Duration.ofMinutes(9));
        assertThat(cleanupJob.run()).isEqualTo(new RecommendationCleanupJob.Result(0, 0));
        RecommendationRequest fresh = insertRequest(user.getId(), RecommendationStatus.PENDING, LocalDateTime.now(clock));

        clock.advance(Duration.ofMinutes(2));
        assertThat(cleanupJob.run().failedPending()).isEqualTo(1);
        assertThat(statusOf(stale.getId())).isEqualTo("FAILED");
        assertThat(statusOf(fresh.getId())).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT failure_reason FROM recommendation_requests WHERE id = ?",
                String.class, stale.getId())).isEqualTo("INTERNAL");

        clock.setNow(start.plusHours(25));
        RecommendationCleanupJob.Result result = cleanupJob.run();
        assertThat(result.clearedOrigins()).isEqualTo(3);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT origin_name, origin_address, origin_lat, origin_lng FROM recommendation_requests WHERE id = ?",
                done.getId());
        assertThat(row.get("origin_name")).isEqualTo("서울역");
        assertThat(row.get("origin_address")).isNull();
        assertThat(row.get("origin_lat")).isNull();
        assertThat(row.get("origin_lng")).isNull();
        assertThat(cleanupJob.run()).isEqualTo(new RecommendationCleanupJob.Result(0, 0));
    }
}
