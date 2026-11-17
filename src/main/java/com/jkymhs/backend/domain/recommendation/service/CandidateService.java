package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.behavior.service.PreferenceProfileService;
import com.jkymhs.backend.domain.behavior.service.ProfileView;
import com.jkymhs.backend.domain.content.entity.IoType;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.recommendation.entity.TravelStyleMapping;
import com.jkymhs.backend.domain.recommendation.repository.TravelStyleMappingRepository;
import com.jkymhs.backend.domain.user.entity.TravelStyle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 추천 후보 고르기 (작업 1단계 CANDIDATES).
 *
 * <ol>
 *   <li><b>후보 풀</b>: 지역·기간이 같은 요청이 같이 쓰는 인기 순 200곳. Redis({@code candidates:*}, 1시간)에 id만 넣어 두고,
 *       없으면 콘텐츠 서비스에서 읽어서 채운다. 회원마다 다른 점수는 풀에 넣지 않는다 (남의 취향이 섞이면 안 되므로, §4 #27).</li>
 *   <li><b>회원 점수</b>: 인기(0~1000 → 0~1) × 0.4 + 취향 프로필(중분류 lcls2, 최대값 기준 −1~1) × 0.4 + 여행 스타일 일치 × 0.2
 *       + 실내 선호(실내 +0.15, 실외 −0.15) − 뺄 곳(다시 추천 제외·직전 코스) 0.5. 반려동물과 가면 동반 불가로 확인된 곳은 뺀다.</li>
 *   <li>상위 40곳 + 꼭 포함할 장소(풀에 없어도)를 점수 순으로 돌려준다.</li>
 * </ol>
 * Redis가 죽으면 캐시 없이 DB에서 바로 읽는다 (G12).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateService {

    static final double POPULARITY_WEIGHT = 0.4;
    static final double PROFILE_WEIGHT = 0.4;
    static final double STYLE_WEIGHT = 0.2;
    static final double INDOOR_BONUS = 0.15;
    static final double PENALTY = 0.5;

    private static final TypeReference<List<Long>> ID_LIST = new TypeReference<>() {
    };

    private final ContentService contentService;
    private final PreferenceProfileService preferenceProfileService;
    private final TravelStyleMappingRepository travelStyleMappingRepository;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final RecommendationProperties properties;

    public List<Candidate> rank(CandidateQuery query) {
        List<ContentSnapshot> pool = pool(query);
        Set<Long> requiredIds = new HashSet<>(query.requiredIds());
        Map<String, Double> profile = normalizedProfile(query);
        Set<String> styleCodes = styleCodes(query.styles());

        List<Candidate> scored = new ArrayList<>();
        for (ContentSnapshot content : pool) {
            if (requiredIds.contains(content.id()) || content.deleted()) {
                continue;
            }
            if (query.pet() && Boolean.FALSE.equals(content.petAllowed())) {
                continue;
            }
            scored.add(new Candidate(content, score(content, query, profile, styleCodes), false));
        }
        scored.sort(ORDER);
        List<Candidate> result = new ArrayList<>(scored.subList(0, Math.min(properties.candidateLimit(), scored.size())));

        Map<Long, ContentSnapshot> required = contentService.findSnapshots(query.requiredIds());
        for (Long id : query.requiredIds()) {
            ContentSnapshot content = required.get(id);
            if (content != null && !content.deleted()) {
                result.add(new Candidate(content, score(content, query, profile, styleCodes), true));
            }
        }
        result.sort(ORDER);
        return result;
    }

    private static final Comparator<Candidate> ORDER = Comparator.comparingDouble(Candidate::score).reversed()
            .thenComparing(c -> -c.content().popularityScore())
            .thenComparing(Candidate::id);

    private static double score(ContentSnapshot content, CandidateQuery query, Map<String, Double> profile,
                                Set<String> styleCodes) {
        double score = Math.min(content.popularityScore(), 1000) / 1000.0 * POPULARITY_WEIGHT;
        if (content.lcls2() != null) {
            score += profile.getOrDefault(content.lcls2(), 0.0) * PROFILE_WEIGHT;
        }
        if (matchesStyle(content, styleCodes)) {
            score += STYLE_WEIGHT;
        }
        if (query.indoor()) {
            if (content.ioType() == IoType.INDOOR) {
                score += INDOOR_BONUS;
            } else if (content.ioType() == IoType.OUTDOOR) {
                score -= INDOOR_BONUS;
            }
        }
        if (query.penalized().contains(content.id())) {
            score -= PENALTY;
        }
        return score;
    }

    private static boolean matchesStyle(ContentSnapshot content, Set<String> codes) {
        for (String code : codes) {
            if (startsWith(content.lcls1(), code) || startsWith(content.lcls2(), code) || startsWith(content.lcls3(), code)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    // 스타일을 고르지 않았으면(건너뜀) 모든 스타일의 코드를 쓴다
    private Set<String> styleCodes(Set<TravelStyle> styles) {
        Set<String> names = new HashSet<>();
        (styles == null || styles.isEmpty() ? Set.of(TravelStyle.values()) : styles)
                .forEach(style -> names.add(style.name()));
        Set<String> codes = new HashSet<>();
        for (TravelStyleMapping mapping : travelStyleMappingRepository.findAll()) {
            if (names.contains(mapping.getStyleCode())) {
                codes.add(mapping.getLclsCode());
            }
        }
        return codes;
    }

    // 프로필 점수를 절댓값 최대 1로 맞춘다 (행동이 많은 회원일수록 원점수가 커져서 인기 점수를 덮지 않게)
    private Map<String, Double> normalizedProfile(CandidateQuery query) {
        ProfileView view = preferenceProfileService.getProfile(query.userId(), query.companion().code());
        Map<String, Double> scores = view.lcls2Scores() == null ? Map.of() : view.lcls2Scores();
        double max = scores.values().stream().filter(Objects::nonNull).mapToDouble(Math::abs).max().orElse(0);
        if (max == 0) {
            return Map.of();
        }
        Map<String, Double> normalized = new LinkedHashMap<>();
        scores.forEach((code, value) -> {
            if (value != null) {
                normalized.put(code, value / max);
            }
        });
        return normalized;
    }

    private List<ContentSnapshot> pool(CandidateQuery query) {
        String key = RecommendationRedisKeys.candidates(query.sidoId(), query.districtIds(), query.startDate(),
                query.endDate());
        Optional<List<Long>> cached = readCache(key);
        if (cached.isPresent()) {
            Map<Long, ContentSnapshot> snapshots = contentService.findSnapshots(cached.get());
            return cached.get().stream().map(snapshots::get).filter(Objects::nonNull).toList();
        }
        List<Long> regionIds = query.districtIds().isEmpty() ? List.of(query.sidoId()) : query.districtIds();
        List<ContentSnapshot> pool = contentService.findCandidatePool(regionIds, query.startDate(), query.endDate(),
                properties.candidatePoolSize());
        writeCache(key, pool.stream().map(ContentSnapshot::id).toList());
        return pool;
    }

    private Optional<List<Long>> readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? Optional.empty() : Optional.of(jsonMapper.readValue(json, ID_LIST));
        } catch (DataAccessException | JacksonException e) {
            log.warn("추천 후보 캐시 조회 실패 (DB에서 읽음): {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void writeCache(String key, List<Long> ids) {
        try {
            redisTemplate.opsForValue().set(key, jsonMapper.writeValueAsString(ids), properties.candidateCacheTtl());
        } catch (DataAccessException | JacksonException e) {
            log.warn("추천 후보 캐시 저장 실패 (무시): {}", e.getMessage());
        }
    }
}
