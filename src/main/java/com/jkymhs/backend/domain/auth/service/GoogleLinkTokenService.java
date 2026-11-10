package com.jkymhs.backend.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 구글 계정 연결 대기 정보 (Redis 키 auth:link:{linkToken}, 값은 JSON 문자열).
 * 구글 로그인 결과가 LINK_REQUIRED일 때 만들고, 로그인 중 구글 계정 연결에 성공하면 지운다.
 *
 * 구글 sub를 응답으로 내려보냈다가 연결 요청에서 다시 받으면 클라이언트가 sub를 바꿔 끼울 수 있다.
 * 그래서 sub·이메일은 서버(Redis)에만 두고, 클라이언트에는 그걸 가리키는 추측 불가능한 linkToken만 준다.
 */
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

    // 10분이 지나 만료됐거나 없는 토큰이면 비어 있다
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
