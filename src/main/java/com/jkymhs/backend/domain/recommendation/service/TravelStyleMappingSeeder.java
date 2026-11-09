package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.entity.TravelStyleMapping;
import com.jkymhs.backend.domain.recommendation.repository.TravelStyleMappingRepository;
import com.jkymhs.backend.domain.user.entity.TravelStyle;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 여행 스타일 → 분류 코드 매핑을 서버 시작 때 채운다. 이미 있는 행은 두고 빠진 행만 넣으며, 목록에 없는 행은 지운다 (몇 번 돌려도 같다).
 * 테스트는 매 테스트 뒤 모든 테이블을 비우므로 필요한 테스트가 {@link #seed()}를 직접 부른다.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class TravelStyleMappingSeeder implements ApplicationRunner {

    // NA 자연, EX 체험, LS 레저, HS 역사, VE 문화시설·관광지, FD 음식, SH 쇼핑, EV 축제·공연
    static final Map<TravelStyle, List<String>> SEED = Map.of(
            TravelStyle.REST, List.of("NA", "EX05", "VE03"),
            TravelStyle.ACTIVITY, List.of("LS", "EX01", "EX02", "EX03", "EX07", "VE02", "VE10"),
            TravelStyle.CULTURE, List.of("HS", "VE04", "VE06", "VE07", "VE09"),
            TravelStyle.FOOD, List.of("FD", "SH06"),
            TravelStyle.NATURE, List.of("NA", "VE03"),
            TravelStyle.SHOPPING, List.of("SH"),
            TravelStyle.FESTIVAL, List.of("EV")
    );

    private final TravelStyleMappingRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        Set<String> wanted = new HashSet<>();
        SEED.forEach((style, codes) -> codes.forEach(code -> wanted.add(style.name() + ":" + code)));
        Set<String> existing = new HashSet<>();
        for (TravelStyleMapping mapping : repository.findAll()) {
            String key = mapping.getStyleCode() + ":" + mapping.getLclsCode();
            if (wanted.contains(key)) {
                existing.add(key);
            } else {
                repository.delete(mapping);
            }
        }
        for (String key : wanted) {
            if (!existing.contains(key)) {
                String[] parts = key.split(":");
                repository.save(TravelStyleMapping.of(parts[0], parts[1]));
            }
        }
    }
}
