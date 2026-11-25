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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreferenceProfileService {

    public static final String COMMON = "common";

    private final BehaviorEventRepository behaviorEventRepository;
    private final PreferenceProfileRepository preferenceProfileRepository;
    private final BehaviorProperties properties;
    private final Clock clock;

    public ProfileView getProfile(Long userId, String companion) {
        if (companion != null) {
            List<PreferenceProfile> rows = preferenceProfileRepository.findAllByUserIdAndProfileKey(userId, companion);
            if (signalCount(rows) >= properties.companionMinSignals()) {
                return toView(rows, true);
            }
        }
        return toView(preferenceProfileRepository.findAllByUserIdAndProfileKey(userId, COMMON), false);
    }

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
