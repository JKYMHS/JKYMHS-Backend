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

/**
 * 회원 탈퇴: 올린 일정을 모두 지우는 DELETE_ALL 작업(토큰 사본 포함, 끝나면 토큰 폐기)을 넣고 연동 행을 지운다.
 * 작업 행은 회원 FK가 없어서 회원이 지워진 뒤에도 남아 구글 정리를 끝낸다.
 *
 * 순서(@Order 가장 먼저): 여행 도메인의 탈퇴 리스너가 여행을 지우기 전에 돌아야 "올린 일정 목록"을 읽을 수 있다.
 * 여행 리스너가 이어서 발행하는 일정 정리 이벤트(CalendarEventsOrphanedEvent)는 연동 행이 이미 없어서 작업을 만들지 않는다
 * (같은 일정을 두 번 지우는 작업이 생기지 않는다).
 *
 * 만료된(REVOKED) 연동은 토큰으로 구글에 접근할 수 없어서 작업 없이 연동 행만 지운다.
 */
@Component
@RequiredArgsConstructor
public class CalendarWithdrawListener {

    private final GoogleCalendarLinkRepository linkRepository;
    private final CalendarJobService jobService;
    private final TripCalendarSupport tripCalendarSupport;

    @EventListener
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
