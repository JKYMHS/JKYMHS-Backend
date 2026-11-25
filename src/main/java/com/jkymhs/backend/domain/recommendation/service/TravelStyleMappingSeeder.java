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

@Component
@Order(2)
@RequiredArgsConstructor
public class TravelStyleMappingSeeder implements ApplicationRunner {
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
