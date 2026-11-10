package com.jkymhs.backend.global.event;

/**
 * 회원 탈퇴 직전에 회원 도메인이 발행한다. 찜·여행·추천·캘린더 도메인이 같은 트랜잭션 안에서(동기 {@code @EventListener})
 * 자기 데이터를 지운다. 회원 행을 지우기 전에 발행하므로 FK가 걸린 자식 행을 먼저 정리할 수 있다.
 *
 * 회원 도메인이 다른 도메인의 Repository를 직접 부르지 않게 하려고 이벤트로 알린다 (도메인 간 의존 방향을 한쪽으로 유지).
 */
public record UserWithdrawnEvent(Long userId) {
}
