package com.jkymhs.backend.global.event;

/**
 * 회원이 행동 데이터 수집 동의를 끈 순간 회원 도메인이 발행한다. 행동 데이터 도메인이 같은 트랜잭션에서
 * 쌓아둔 행동 기록·취향 프로필을 바로 지운다 (동의 철회 = 즉시 삭제, 동의 변경이 롤백되면 삭제도 같이 롤백).
 */
public record BehaviorConsentRevokedEvent(Long userId) {
}
