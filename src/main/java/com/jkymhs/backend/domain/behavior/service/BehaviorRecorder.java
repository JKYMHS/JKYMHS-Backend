package com.jkymhs.backend.domain.behavior.service;

import com.jkymhs.backend.domain.behavior.entity.BehaviorEvent;
import com.jkymhs.backend.domain.behavior.entity.ContentSaveLog;
import com.jkymhs.backend.domain.behavior.repository.BehaviorEventRepository;
import com.jkymhs.backend.domain.behavior.repository.ContentSaveLogRepository;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.domain.user.service.UserConsentService;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 행동 하나를 저장하고 프로필을 다시 계산한다. {@link BehaviorSignalListener}가 부른다.
 *
 * 항상 새 트랜잭션(REQUIRES_NEW)으로 돈다. 리스너는 원래 요청의 트랜잭션이 커밋된 "뒤"에 불리는데,
 * 테스트처럼 같은 스레드에서 바로 실행되면 끝난 트랜잭션이 아직 스레드에 묶여 있다.
 * 그 상태에서 기본값(REQUIRED)으로 참여하면 쓰기가 커밋되지 않고 사라진다. 그래서 따로 연다.
 */
@Service
@RequiredArgsConstructor
public class BehaviorRecorder {

    private final ContentSaveLogRepository contentSaveLogRepository;
    private final BehaviorEventRepository behaviorEventRepository;
    private final PreferenceProfileService preferenceProfileService;
    private final UserConsentService userConsentService;
    private final ContentService contentService;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(BehaviorSignalEvent event) {
        LocalDateTime occurredAt = (event.occurredAt() != null ? event.occurredAt() : LocalDateTime.now(clock))
                .truncatedTo(ChronoUnit.MICROS);

        // 인기 점수 입력. 개인 취향이 아니라 전체 통계라서 동의와 상관없이 남긴다
        if (event.type() == BehaviorSignalType.SCHEDULE_SAVE) {
            contentSaveLogRepository.save(ContentSaveLog.create(event.userId(), event.contentId(), occurredAt));
        }

        if (!userConsentService.isAgreed(event.userId(), ConsentType.BEHAVIOR_DATA)) {
            return;
        }
        // 삭제된 콘텐츠도 분류는 남아 있어서 그대로 쓴다 (찜해 둔 뒤 내려간 콘텐츠도 취향 신호다)
        ContentSnapshot content = contentService.findSnapshots(List.of(event.contentId())).get(event.contentId());
        String lcls2 = content == null ? null : content.lcls2();
        behaviorEventRepository.save(BehaviorEvent.create(event.userId(), event.contentId(), lcls2, event.type(),
                event.companion(), event.tripId(), occurredAt));

        preferenceProfileService.recompute(event.userId(), PreferenceProfileService.COMMON);
        if (event.companion() != null) {
            preferenceProfileService.recompute(event.userId(), event.companion());
        }
    }
}
