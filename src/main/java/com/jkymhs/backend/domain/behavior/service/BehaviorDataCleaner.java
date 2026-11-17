package com.jkymhs.backend.domain.behavior.service;

import com.jkymhs.backend.domain.behavior.repository.BehaviorEventRepository;
import com.jkymhs.backend.domain.behavior.repository.ContentSaveLogRepository;
import com.jkymhs.backend.domain.behavior.repository.ContentViewRepository;
import com.jkymhs.backend.domain.behavior.repository.PreferenceProfileRepository;
import com.jkymhs.backend.global.event.BehaviorConsentRevokedEvent;
import com.jkymhs.backend.global.event.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동의 철회·탈퇴 때 행동 데이터를 지운다. 둘 다 동기 {@code @EventListener}라 발행한 쪽(회원 도메인)의 트랜잭션 안에서 돈다.
 * 삭제가 실패하면 동의 변경·탈퇴도 같이 롤백된다 ("껐는데 데이터가 남는" 상태가 생기지 않는다).
 */
@Component
@RequiredArgsConstructor
public class BehaviorDataCleaner {

    private final BehaviorEventRepository behaviorEventRepository;
    private final PreferenceProfileRepository preferenceProfileRepository;
    private final ContentViewRepository contentViewRepository;
    private final ContentSaveLogRepository contentSaveLogRepository;

    /**
     * 행동 데이터 동의를 끄면 취향에 쓰는 기록(행동 이벤트·프로필)만 지운다.
     * 조회 기록·일정 저장 기록은 인기 점수용 전체 통계라서 동의 대상이 아니다 (탈퇴하면 지운다).
     */
    @EventListener
    @Transactional
    public void onConsentRevoked(BehaviorConsentRevokedEvent event) {
        deleteProfileData(event.userId());
    }

    /** 회원 행보다 먼저 지워야 FK에 걸리지 않는다 (회원 도메인이 회원을 지우기 전에 발행한다). */
    @EventListener
    @Transactional
    public void onUserWithdrawn(UserWithdrawnEvent event) {
        deleteProfileData(event.userId());
        contentViewRepository.deleteAllByUserId(event.userId());
        contentSaveLogRepository.deleteAllByUserId(event.userId());
    }

    private void deleteProfileData(Long userId) {
        behaviorEventRepository.deleteAllByUserId(userId);
        preferenceProfileRepository.deleteAllByUserId(userId);
    }
}
