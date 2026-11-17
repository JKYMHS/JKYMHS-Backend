package com.jkymhs.backend.domain.behavior.service;

import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 찜·조회·여행·추천·캘린더 도메인이 발행한 {@link BehaviorSignalEvent}를 받는다.
 *
 * <ul>
 *   <li>AFTER_COMMIT: 찜 추가가 롤백되면 "찜했다"는 행동도 남으면 안 된다. 원래 작업이 커밋된 뒤에만 기록한다.</li>
 *   <li>{@code @Async}: 동의 확인·저장·프로필 재계산(최근 180일 합산)을 응답 스레드에서 하지 않는다.
 *       여기서 실패해도 찜·조회 API는 이미 성공 응답을 보냈다 (행동 기록은 부가 기능이다).</li>
 *   <li>{@code fallbackExecution = true}: 트랜잭션 밖에서 발행한 이벤트도 버리지 않고 바로 처리한다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class BehaviorSignalListener {

    private final BehaviorRecorder behaviorRecorder;

    @Async(BehaviorConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(BehaviorSignalEvent event) {
        behaviorRecorder.record(event);
    }
}
