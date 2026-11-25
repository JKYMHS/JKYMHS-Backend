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

        if (event.type() == BehaviorSignalType.SCHEDULE_SAVE) {
            contentSaveLogRepository.save(ContentSaveLog.create(event.userId(), event.contentId(), occurredAt));
        }

        if (!userConsentService.isAgreed(event.userId(), ConsentType.BEHAVIOR_DATA)) {
            return;
        }
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
