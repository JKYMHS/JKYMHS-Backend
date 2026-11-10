package com.jkymhs.backend.domain.behavior.service;

import com.jkymhs.backend.domain.behavior.repository.ContentViewRepository;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 상세 조회 기록. 로그인한 회원이 같은 콘텐츠를 하루(KST)에 몇 번 열든 한 번만 센다.
 * 비로그인 조회는 세지 않는다 (누구인지 몰라서 "하루 1회"를 지킬 수 없고, 새로고침만으로 인기 점수를 올릴 수 있게 된다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentViewService {

    private final ContentViewRepository contentViewRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 오늘 처음 본 것이면 기록하고 VIEW 행동 이벤트를 발행한다. 이벤트는 이 트랜잭션이 커밋된 뒤
     * {@link BehaviorSignalListener}가 받는다 (동의 확인·프로필 계산은 응답 뒤에 따로 돈다).
     *
     * @return 오늘 처음 본 것이면 true
     */
    @Transactional
    public boolean recordView(Long userId, Long contentId) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        boolean first = contentViewRepository.insertIgnore(userId, contentId, now.toLocalDate(), now) == 1;
        if (first) {
            eventPublisher.publishEvent(
                    new BehaviorSignalEvent(userId, contentId, BehaviorSignalType.VIEW, null, null, now));
        }
        return first;
    }
}
