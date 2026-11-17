package com.jkymhs.backend.domain.user.service;

import java.util.Map;

/**
 * 탈퇴 안내 화면의 "지워지는 데이터" 숫자를 채운다. 여행·찜·캘린더 도메인이 각자 구현해서 빈으로 등록한다.
 * 회원 도메인이 다른 도메인의 Repository를 모르게 하려고, 숫자를 세는 일을 각 도메인에 맡긴다 (의존 방향: 다른 도메인 → 회원).
 *
 * 키: tripCount·planCount(여행), wishCount(찜), calendarEventCount(캘린더). 넣지 않은 키는 0으로 나간다.
 */
public interface WithdrawSummaryContributor {

    void contribute(Long userId, Map<String, Long> counts);
}
