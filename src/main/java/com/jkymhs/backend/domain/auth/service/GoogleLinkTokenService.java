package com.jkymhs.backend.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleLinkTokenService {

    public static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public String issue(Long memberId, String googleSub, String googleEmail) {
        String linkToken = UUID.randomUUID().toString();
        String value = jsonMapper.writeValueAsString(new LinkData(memberId, googleSub, googleEmail));
        redisTemplate.opsForValue().set(AuthRedisKeys.googleLink(linkToken), value, TOKEN_TTL);
        return linkToken;
    }

    public Optional<LinkData> find(String linkToken) {
        String value = redisTemplate.opsForValue().get(AuthRedisKeys.googleLink(linkToken));
        return Optional.ofNullable(value).map(json -> jsonMapper.readValue(json, LinkData.class));
    }

    public void delete(String linkToken) {
        redisTemplate.delete(AuthRedisKeys.googleLink(linkToken));
    }

    public record LinkData(Long memberId, String googleSub, String googleEmail) {
    }
}
