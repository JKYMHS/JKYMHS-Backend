package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.RecommendationTestSupport;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationWithdrawListenerTest extends RecommendationTestSupport {

    @Test
    @DisplayName("탈퇴하면 그 회원의 추천 요청·지역·꼭 포함 장소와 Redis 결과가 지워지고, 다른 회원 것은 남는다")
    void withdrawDeletesRecommendations() throws Exception {
        loadSeoulPlaces();
        User user = saveUser("leave@example.com");
        String token = token(user);
        long mine = recommend(token, body(TODAY, TODAY, "friends",
                ", \"districtIds\": [%d], \"requiredContentIds\": [1005]".formatted(jongnoId)));
        long kept = recommend(token(saveUser("stay@example.com")), body(TODAY, TODAY, "friends", ""));
        assertThat(redisTemplate.hasKey(RecommendationRedisKeys.result(mine))).isTrue();

        mockMvc.perform(json(delete("/api/members/me"), token, """
                {"password": "%s"}
                """.formatted(PASSWORD))).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForList("SELECT id FROM recommendation_requests", Long.class)).containsExactly(kept);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recommendation_request_regions", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recommendation_required_places", Long.class)).isZero();
        assertThat(redisTemplate.hasKey(RecommendationRedisKeys.result(mine))).isFalse();
        assertThat(redisTemplate.hasKey(RecommendationRedisKeys.progress(mine))).isFalse();
        assertThat(redisTemplate.hasKey(RecommendationRedisKeys.result(kept))).isTrue();
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }
}
