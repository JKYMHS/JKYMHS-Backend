package com.jkymhs.backend.domain.wish.service;

import com.jkymhs.backend.domain.user.service.WithdrawSummaryContributor;
import com.jkymhs.backend.domain.user.service.WithdrawSummaryService;
import com.jkymhs.backend.global.event.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WishWithdrawHandler implements WithdrawSummaryContributor {
    private final WishService wishService;

    @Override
    public void contribute(Long userId, Map<String, Long> counts) {
        counts.put(WithdrawSummaryService.WISH_COUNT, wishService.countByUser(userId));
    }

    @EventListener
    public void onUserWithdrawn(UserWithdrawnEvent event) {
        wishService.deleteAllOf(event.userId());
    }
}
