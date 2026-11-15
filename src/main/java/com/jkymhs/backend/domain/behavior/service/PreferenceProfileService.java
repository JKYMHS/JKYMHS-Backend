package com.jkymhs.backend.domain.behavior.service;

import com.jkymhs.backend.domain.behavior.entity.BehaviorEvent;
import com.jkymhs.backend.domain.behavior.entity.PreferenceProfile;
import com.jkymhs.backend.domain.behavior.repository.BehaviorEventRepository;
import com.jkymhs.backend.domain.behavior.repository.PreferenceProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 취향 프로필 계산·조회.
 *
 * 점수 = Σ 가중치 × 0.5^(경과일 / 30) — 최근 180일의 행동을 분류 중분류(lcls2)별로 더한다.
 * 30일 전 찜(3점)은 1.5점, 60일 전 찜은 0.75점이 된다 (반감기). 취향이 바뀌면 옛 행동의 영향이 서서히 줄어든다.
 *
 * 프로필은 두 종류다. {@code common}은 모든 행동, 동행 키("kids" 등)는 그 동행으로 한 행동(일정 저장·방문 확인 등)만 쓴다.
 * 조회·찜처럼 동행을 모르는 행동은 common에만 들어간다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreferenceProfileService {

    public static final String COMMON = "common";

    private final BehaviorEventRepository behaviorEventRepository;
    private final PreferenceProfileRepository preferenceProfileRepository;
    private final BehaviorProperties properties;
    private final Clock clock;

    /**
     * D5(AI 추천) 계약. 동행 프로필의 행동이 {@code behavior.companion-min-signals}(10)개보다 적으면
     * 공통 프로필을 준다 (행동 몇 개로 만든 프로필은 우연에 크게 흔들린다).
     *
     * @param companion 동행 FE 코드. null이면 공통 프로필
     */
    public ProfileView getProfile(Long userId, String companion) {
        if (companion != null) {
            List<PreferenceProfile> rows = preferenceProfileRepository.findAllByUserIdAndProfileKey(userId, companion);
            if (signalCount(rows) >= properties.companionMinSignals()) {
                return toView(rows, true);
            }
        }
        return toView(preferenceProfileRepository.findAllByUserIdAndProfileKey(userId, COMMON), false);
    }

    /**
     * 그 회원의 한 프로필을 행동 기록에서 처음부터 다시 계산해서 저장한다.
     * 점수가 시간에 따라 줄어드는 식이라 "기존 점수 + 새 행동"으로 더할 수 없어서, 매번 180일치를 다시 더한다 (회원당 수백 건 수준).
     *
     * @param profileKey {@link #COMMON} 또는 동행 코드
     */
    @Transactional
    public void recompute(Long userId, String profileKey) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        LocalDateTime since = now.minus(properties.profileWindow());
        List<BehaviorEvent> events = COMMON.equals(profileKey)
                ? behaviorEventRepository.findAllByUserIdAndOccurredAtGreaterThanEqual(userId, since)
                : behaviorEventRepository.findAllByUserIdAndCompanionAndOccurredAtGreaterThanEqual(userId, profileKey, since);

        Map<String, Aggregate> byLcls2 = new TreeMap<>();
        for (BehaviorEvent event : events) {
            if (event.getLcls2() == null) {
                continue;
            }
            byLcls2.computeIfAbsent(event.getLcls2(), key -> new Aggregate())
                    .add(event.getWeight() * decay(event.getOccurredAt(), now));
        }

        if (byLcls2.isEmpty()) {
            preferenceProfileRepository.deleteAllByUserIdAndProfileKey(userId, profileKey);
            return;
        }
        preferenceProfileRepository.deleteOthers(userId, profileKey, byLcls2.keySet());
        byLcls2.forEach((lcls2, aggregate) -> preferenceProfileRepository.upsert(userId, profileKey, lcls2,
                BigDecimal.valueOf(aggregate.score).setScale(4, RoundingMode.HALF_UP), aggregate.count, now));
    }

    // 0.5^(경과일 / 반감기). 경과일은 초 단위로 계산해서 소수로 쓴다 (같은 날 행동끼리도 순서가 반영된다)
    private double decay(LocalDateTime occurredAt, LocalDateTime now) {
        double ageSeconds = Math.max(0, Duration.between(occurredAt, now).toSeconds());
        double halfLifeSeconds = properties.halfLife().toSeconds();
        return Math.pow(0.5, ageSeconds / halfLifeSeconds);
    }

    private static int signalCount(List<PreferenceProfile> rows) {
        return rows.stream().mapToInt(PreferenceProfile::getSignalCount).sum();
    }

    private static ProfileView toView(List<PreferenceProfile> rows, boolean companionSpecific) {
        Map<String, Double> scores = new LinkedHashMap<>();
        rows.forEach(row -> scores.put(row.getLcls2(), row.getScore().doubleValue()));
        return new ProfileView(scores, signalCount(rows), companionSpecific);
    }

    private static final class Aggregate {
        private double score;
        private int count;

        void add(double weighted) {
            score += weighted;
            count++;
        }
    }
}
