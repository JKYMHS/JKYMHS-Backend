package com.jkymhs.backend.domain.calendar.service;

import com.jkymhs.backend.domain.calendar.repository.GoogleCalendarLinkRepository;
import com.jkymhs.backend.domain.trip.service.TripCalendarSupport;
import com.jkymhs.backend.global.event.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CalendarWithdrawListener {
    private final GoogleCalendarLinkRepository linkRepository;
    private final CalendarJobService jobService;
    private final TripCalendarSupport tripCalendarSupport;

    @EventListener
    // 여행 도메인 탈퇴 리스너보다 먼저 실행해야 함
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onUserWithdrawn(UserWithdrawnEvent event) {
        Long userId = event.userId();
        linkRepository.findByUserId(userId).ifPresent(link -> {
            if (link.isActive()) {
                List<CalendarJobOp> ops = tripCalendarSupport.uploadedItems(userId).stream()
                        .map(CalendarJobOp::delete).toList();
                jobService.createDeleteAll(userId, link.getEncryptedRefreshToken(), ops);
            }
            linkRepository.deleteByUserIdInBulk(userId);
        });
    }
}
