package com.jkymhs.backend.domain.behavior;

import com.jkymhs.backend.domain.behavior.entity.PreferenceProfile;
import com.jkymhs.backend.domain.behavior.repository.BehaviorEventRepository;
import com.jkymhs.backend.domain.behavior.repository.ContentSaveLogRepository;
import com.jkymhs.backend.domain.behavior.repository.PreferenceProfileRepository;
import com.jkymhs.backend.domain.behavior.service.PreferenceProfileService;
import com.jkymhs.backend.domain.behavior.service.ProfileView;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.wish.BehaviorTestSupport;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static com.jkymhs.backend.domain.content.ContentFixtures.attraction;
import static com.jkymhs.backend.domain.content.ContentFixtures.content;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 행동 이벤트 → 저장·프로필 계산. 테스트에서는 실행기가 동기라서 publishEvent가 돌아오면 처리가 끝나 있다.
 * 콘텐츠 1(경복궁)은 분류 HS01(역사유적지), 2(박물관)는 VE07(박물관·전시관)이다.
 */
class BehaviorSignalListenerTest extends BehaviorTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 10, 0);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ContentSaveLogRepository contentSaveLogRepository;

    @Autowired
    private BehaviorEventRepository behaviorEventRepository;

    @Autowired
    private PreferenceProfileRepository preferenceProfileRepository;

    @Autowired
    private PreferenceProfileService preferenceProfileService;

    @BeforeEach
    void setUp() {
        saveContents(attraction(1L, "경복궁", "11", "110"),
                content(2L, ContentType.ATTRACTION, "박물관", "11", "140", "VE", "VE07", "VE070100", null, null));
    }

    @Test
    @DisplayName("행동 데이터에 동의하지 않았으면 일정 저장 기록(인기 점수용)만 남고 행동 기록·프로필은 없다")
    void withoutConsentOnlySaveLog() {
        User user = saveUser(false);

        publish(user, 1L, BehaviorSignalType.SCHEDULE_SAVE, "kids", NOW);
        publish(user, 1L, BehaviorSignalType.WISH, null, NOW);

        assertThat(contentSaveLogRepository.findAll()).singleElement()
                .satisfies(log -> assertThat(log.getContentId()).isEqualTo(1L));
        assertThat(behaviorEventRepository.count()).isZero();
        assertThat(preferenceProfileRepository.count()).isZero();
    }

    @Test
    @DisplayName("동의했으면 행동을 저장하고 공통·동행 프로필을 계산한다")
    void withConsentEventAndProfiles() {
        User user = saveUser(true);

        publish(user, 1L, BehaviorSignalType.SCHEDULE_SAVE, "kids", NOW);

        assertThat(contentSaveLogRepository.count()).isEqualTo(1);
        assertThat(behaviorEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getWeight()).isEqualTo(5);
            assertThat(event.getLcls2()).isEqualTo("HS01");
            assertThat(event.getCompanion()).isEqualTo("kids");
        });
        assertThat(preferenceProfileRepository.findAll())
                .extracting(PreferenceProfile::getProfileKey, PreferenceProfile::getLcls2, PreferenceProfile::getSignalCount)
                .containsExactlyInAnyOrder(
                        tuple("common", "HS01", 1),
                        tuple("kids", "HS01", 1));
    }

    @Test
    @DisplayName("점수는 30일 반감기로 줄고, 180일보다 오래된 행동은 빠진다")
    void halfLifeAndWindow() {
        User user = saveUser(true);

        publish(user, 1L, BehaviorSignalType.WISH, null, NOW.minusDays(30));   // 3 × 0.5 = 1.5
        publish(user, 1L, BehaviorSignalType.VIEW, null, NOW);                 // 1
        publish(user, 2L, BehaviorSignalType.VISIT_SATISFIED, null, NOW.minusDays(60)); // 10 × 0.25 = 2.5
        publish(user, 2L, BehaviorSignalType.SCHEDULE_SAVE, null, NOW.minusDays(181));  // 빠짐

        ProfileView profile = preferenceProfileService.getProfile(user.getId(), null);
        assertThat(profile.companionSpecific()).isFalse();
        assertThat(profile.signalCount()).isEqualTo(3);
        assertThat(profile.lcls2Scores().get("HS01")).isCloseTo(2.5, within(0.0001));
        assertThat(profile.lcls2Scores().get("VE07")).isCloseTo(2.5, within(0.0001));
    }

    @Test
    @DisplayName("동행 프로필의 행동이 10개보다 적으면 공통 프로필을 주고, 10개가 되면 동행 프로필을 준다")
    void companionNeedsTenSignals() {
        User user = saveUser(true);
        publish(user, 2L, BehaviorSignalType.WISH, null, NOW);
        for (int i = 0; i < 9; i++) {
            publish(user, 1L, BehaviorSignalType.SCHEDULE_SAVE, "kids", NOW);
        }

        ProfileView fewSignals = preferenceProfileService.getProfile(user.getId(), "kids");
        assertThat(fewSignals.companionSpecific()).isFalse();
        assertThat(fewSignals.signalCount()).isEqualTo(10);
        assertThat(fewSignals.lcls2Scores()).containsOnlyKeys("HS01", "VE07");

        publish(user, 1L, BehaviorSignalType.SCHEDULE_SAVE, "kids", NOW);

        ProfileView enough = preferenceProfileService.getProfile(user.getId(), "kids");
        assertThat(enough.companionSpecific()).isTrue();
        assertThat(enough.signalCount()).isEqualTo(10);
        assertThat(enough.lcls2Scores()).containsOnlyKeys("HS01");
        assertThat(enough.lcls2Scores().get("HS01")).isCloseTo(50.0, within(0.0001));
    }

    @Test
    @DisplayName("찜을 추가하면 커밋 뒤 WISH 행동이 기록된다")
    void wishApiPublishesSignal() throws Exception {
        User user = saveUser(true);

        mockMvc.perform(auth(post("/api/wishes/2"), token(user))).andExpect(status().isOk());

        assertThat(behaviorEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo(BehaviorSignalType.WISH);
            assertThat(event.getLcls2()).isEqualTo("VE07");
        });
        assertThat(preferenceProfileService.getProfile(user.getId(), null).lcls2Scores())
                .containsEntry("VE07", 3.0);
    }

    private void publish(User user, long contentId, BehaviorSignalType type, String companion, LocalDateTime at) {
        eventPublisher.publishEvent(new BehaviorSignalEvent(user.getId(), contentId, type, companion, null, at));
    }
}
