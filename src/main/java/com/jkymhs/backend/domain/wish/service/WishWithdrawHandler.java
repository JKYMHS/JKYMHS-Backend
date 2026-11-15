package com.jkymhs.backend.domain.wish.service;

import com.jkymhs.backend.domain.user.service.WithdrawSummaryContributor;
import com.jkymhs.backend.domain.user.service.WithdrawSummaryService;
import com.jkymhs.backend.global.event.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 탈퇴와 찜. 회원 도메인이 찜 테이블을 모르게, 숫자 세기(탈퇴 안내)와 지우기(탈퇴)를 찜 도메인이 맡는다.
 */
@Component
@RequiredArgsConstructor
public class WishWithdrawHandler implements WithdrawSummaryContributor {

    private final WishService wishService;

    @Override
    public void contribute(Long userId, Map<String, Long> counts) {
        counts.put(WithdrawSummaryService.WISH_COUNT, wishService.countByUser(userId));
    }

    /** 탈퇴 트랜잭션 안에서(동기) 회원 행보다 먼저 지운다 (wishlists.user_id FK) */
    @EventListener
    public void onUserWithdrawn(UserWithdrawnEvent event) {
        wishService.deleteAllOf(event.userId());
    }
}
